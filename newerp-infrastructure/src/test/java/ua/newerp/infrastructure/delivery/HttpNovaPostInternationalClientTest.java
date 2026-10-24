package ua.newerp.infrastructure.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ua.newerp.application.delivery.CreatedShipment;
import ua.newerp.application.delivery.HsCodeDto;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.delivery.InternationalShipment;
import ua.newerp.domain.delivery.SeatPreset;

/** The HTTP calls against a mocked server; the company, the recipient and the answers are invented. */
class HttpNovaPostInternationalClientTest {

    static final String API = "https://npi.test/v.1.0";
    static final String AUTH = API + "/clients/authorization?apiKey=secret-key";

    MockRestServiceServer server;
    HttpNovaPostInternationalClient client;

    final InternationalShipment shipment = InternationalShipment.of(1001,
            new InternationalShipment.Recipient("Anna", "Kowalska", "48600100200", "anna@example.com", "PL", "", "Kraków", "Floriańska",
                    "5", "2", "31-019"),
            null, List.of(new InternationalShipment.Item("Сукня лляна", "Linen dress", "6204491000", 450, BigDecimal.ONE, new BigDecimal("3000"))),
            List.of(SeatPreset.ALL.getFirst().seat()), new BigDecimal("3000"), "UAH");

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var sender = new NovaPostInternationalProperties.Sender("12345678", "ТОВ Тест", "Іван Тестовий", "+380 50 000 00 00", "shop@example.com",
                "Київ", "вул. Тестова", "1", "", "01001");
        client = new HttpNovaPostInternationalClient(builder.build(), new NovaPostInternationalProperties("secret-key", false, API, false, sender));
    }

    @Test
    void createsAShipmentWithTheJwtAsIsAndTheOldSitesPayload() {
        server.expect(requestTo(AUTH)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"jwt\":\"token-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/shipments")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "token-1"))
                .andExpect(content().json("""
                        {"status":"ReadyToShip","clientOrder":"1001","payerType":"Sender",
                         "invoice":{"incoterm":"DAP","exportReason":"Selling","cost":3000,"currency":"UAH","payerFeesCustoms":"Recipient",
                                    "items":[{"hsCode":"6204491000","nameEng":"Linen dress","actualWeight":450,"measurementCode":"pieces"}]},
                         "parcels":[{"rowNumber":1,"width":310,"length":360,"height":60,"actualWeight":450,"insuranceCost":3000.00,
                                     "cargoCategory":"parcel"}],
                         "sender":{"companyTin":"12345678","phone":"380500000000","countryCode":"UA",
                                   "registrationAddressParts":{"city":"Київ","postCode":"01001","region":"","note":""}},
                         "recipient":{"name":"Anna Kowalska","countryCode":"PL","addressParts":{"city":"Kraków","building":"5","flat":"2"}}}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"sh-77\",\"number\":\"NP000123456UA\",\"status\":\"ReadyToShip\",\"cost\":812.5}"));

        assertThat(client.create(shipment)).isEqualTo(new CreatedShipment("sh-77", "NP000123456UA", new BigDecimal("812.5")));
        server.verify();
    }

    @Test
    void takesANewJwtOnceWhenTheOldOneExpired() {
        server.expect(requestTo(AUTH)).andRespond(withSuccess("{\"jwt\":\"token-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(API + "/dictionary/classifier"))).andExpect(header("Authorization", "token-1"))
                .andRespond(withSuccess("""
                        {"items":[{"hsCode":"6204491000","product":{"currentLocal":"Сукня","en":"Dress"},
                                   "subCategory":{"currentLocal":"З льону"}}]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(API + "/dictionary/classifier"))).andExpect(header("Authorization", "token-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(AUTH)).andRespond(withSuccess("{\"jwt\":\"token-2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(API + "/dictionary/classifier"))).andExpect(header("Authorization", "token-2"))
                .andExpect(queryParam("keyword", "dress")).andExpect(queryParam("country-code", "PL"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.searchHsCodes("dress", "PL")).containsExactly(new HsCodeDto("6204491000", "Сукня — З льону", "Dress"));
        assertThat(client.searchHsCodes("dress", "PL")).isEmpty();
        server.verify();
    }

    @Test
    void apiErrorsBecomeAReadableMessage() {
        server.expect(requestTo(AUTH)).andRespond(withSuccess("{\"jwt\":\"token-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/shipments")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errors\":{\"recipient.addressParts.postCode\":\"Invalid postcode\",\"invoice.items.0.hsCode\":\"Unknown code\"}}"));

        assertThatThrownBy(() -> client.create(shipment)).isInstanceOf(DomainException.class)
                .hasMessage("Nova Post International відмовила (HTTP 422): recipient.addressParts.postCode: Invalid postcode; "
                        + "invoice.items.0.hsCode: Unknown code");
    }

    @Test
    void aRefusedKeyIsSaidSo() {
        server.expect(requestTo(AUTH)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.delete("sh-77")).hasMessage("Nova Post International не прийняла ключ API (HTTP 401).");
    }

    @Test
    void deletesByTheShipmentId() {
        server.expect(requestTo(AUTH)).andRespond(withSuccess("{\"jwt\":\"token-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/shipments/sh-77")).andExpect(method(HttpMethod.DELETE)).andExpect(header("Authorization", "token-1"))
                .andRespond(withSuccess());

        client.delete("sh-77");
        server.verify();
    }
}
