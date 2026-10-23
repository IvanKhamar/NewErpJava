package ua.newerp.infrastructure.cashbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ua.newerp.domain.cashbox.FiscalReceipt;
import ua.newerp.domain.cashbox.ReceiptSourceLine;
import ua.newerp.domain.common.DomainException;

/** The client against a fake HTTP server (MockRestServiceServer): no request leaves the test. Keys are invented. */
class HttpCheckboxClientTest {

    static final String API = "https://checkbox.test";
    static final String SIGN_IN = "{\"access_token\": \"token-1\"}";

    MockRestServiceServer server;
    HttpCheckboxClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new CheckboxProperties(API + "/", null, null, false, null,
                List.of(new CheckboxProperties.Cashier(1, "olena", "pin", "license-1")));
        client = new HttpCheckboxClient(builder.build(), properties, Duration.ZERO);
    }

    @Test
    void signsInOnceAndSendsTheReceiptInKopecks() {
        expectSignIn();
        server.expect(requestTo(API + "/api/v1/receipts/sell")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andExpect(header("X-Client-Name", "NewErp"))
                .andExpect(headerDoesNotExist("X-License-Key"))
                .andExpect(content().json("""
                        {"cashier_name":"Олена","goods":[{"good":{"code":"101","name":"Сукня","price":75000},"quantity":2000}],
                         "payments":[{"type":"CASHLESS","value":120000,"label":"Оплата в Nova pay"}],
                         "discounts":[{"type":"DISCOUNT","mode":"VALUE","value":30000}],
                         "delivery":{"email":"a@b.ua"},"footer":"Замовлення № 42"}
                        """))
                .andRespond(json(HttpStatus.CREATED, "{\"id\": \"r-1\"}"));
        server.expect(requestTo(API + "/api/v1/receipts/sell")).andRespond(json(HttpStatus.CREATED, "{\"id\": \"r-2\"}"));

        FiscalReceipt receipt = FiscalReceipt.build(42, "Олена", List.of(new ReceiptSourceLine(101, "Сукня", new BigDecimal("750"), 2)),
                new BigDecimal("1200"), "cod", "", "a@b.ua");

        assertThat(client.sell(1, receipt)).isEqualTo("r-1");
        assertThat(client.sell(1, receipt)).isEqualTo("r-2"); // the token is kept, no second sign-in
        server.verify();
    }

    @Test
    void anExpiredTokenMeansSigningInAgainAndRepeating() {
        expectSignIn();
        server.expect(requestTo(API + "/api/v1/reports")).andRespond(json(HttpStatus.UNAUTHORIZED, "{\"message\": \"Invalid token\"}"));
        server.expect(requestTo(API + "/api/v1/cashier/signin")).andRespond(withSuccess("{\"access_token\": \"token-2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v1/reports")).andExpect(header("Authorization", "Bearer token-2"))
                .andRespond(json(HttpStatus.CREATED, "{\"id\": \"x-1\"}"));

        assertThat(client.createXReport(1)).isEqualTo("x-1");
        server.verify();
    }

    @Test
    void openShiftTakesAnAlreadyOpenOne() {
        expectSignIn();
        server.expect(requestTo(API + "/api/v1/cashier/shift"))
                .andRespond(withSuccess("{\"id\": \"s-open\", \"status\": \"OPENED\"}", MediaType.APPLICATION_JSON));

        assertThat(client.openShift(1)).isEqualTo("s-open");
    }

    @Test
    void openShiftCreatesOneWithTheLicenseKeyWhenNoneIsOpen() {
        expectSignIn();
        server.expect(requestTo(API + "/api/v1/cashier/shift")).andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v1/shifts")).andExpect(method(HttpMethod.POST)).andExpect(header("X-License-Key", "license-1"))
                .andRespond(json(HttpStatus.ACCEPTED, "{\"id\": \"s-new\", \"status\": \"CREATED\"}"));

        assertThat(client.openShift(1)).isEqualTo("s-new");
        server.verify();
    }

    @Test
    void closeShiftWaitsForTheZReport() {
        expectSignIn();
        server.expect(requestTo(API + "/api/v1/shifts/close"))
                .andRespond(json(HttpStatus.ACCEPTED, "{\"id\": \"s-1\", \"status\": \"CLOSING\", \"z_report\": null}"));
        server.expect(requestTo(API + "/api/v1/shifts/s-1"))
                .andRespond(withSuccess("{\"id\": \"s-1\", \"status\": \"CLOSING\", \"z_report\": null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v1/shifts/s-1"))
                .andRespond(withSuccess("{\"id\": \"s-1\", \"status\": \"CLOSED\", \"z_report\": {\"id\": \"z-1\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.closeShift(1)).isEqualTo("z-1");
        server.verify();
    }

    @Test
    void aRefusalBecomesADomainErrorWithCheckboxsMessage() {
        expectSignIn();
        server.expect(requestTo(API + "/api/v1/reports")).andRespond(json(HttpStatus.BAD_REQUEST, "{\"message\": \"Зміну не відкрито\"}"));

        assertThatThrownBy(() -> client.createXReport(1)).isInstanceOf(DomainException.class)
                .hasMessage("Checkbox відмовив (400): Зміну не відкрито");
    }

    @Test
    void anUnknownCashierCannotBeUsedAndTheReceiptUrlHasNoSecrets() {
        assertThat(client.canUse(2)).isFalse();
        assertThat(client.canUse(1)).isTrue();
        assertThat(client.receiptUrl("r-1")).isEqualTo("https://checkbox.test/api/v1/receipts/r-1/html");
    }

    private void expectSignIn() {
        server.expect(requestTo(API + "/api/v1/cashier/signin")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"login\":\"olena\",\"password\":\"pin\"}"))
                .andRespond(withSuccess(SIGN_IN, MediaType.APPLICATION_JSON));
    }

    private static org.springframework.test.web.client.ResponseCreator json(HttpStatus status, String body) {
        return withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
