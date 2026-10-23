package ua.newerp.api.payments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;
import ua.newerp.domain.payments.LiqPayState;
import ua.newerp.domain.payments.MatchReason;

/** Without bank and LiqPay keys the stubs are used, as in local development. */
@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/payments.sql"})
class PaymentControllerTest extends IntegrationTest {

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void suggestLinkAndHideAPayment() {
        assertThat(mvc.get().uri("/payments/bank?linked=false").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"totalCount":1,"items":[{"id":1,"amount":800.00,"balance":5000.00,"payer":"Петро Мельник","orderId":0,"isVisible":true}]}
                """);

        // "2 шт" is too short for an order number, so the sum and the surname match
        assertThat(mvc.get().uri("/payments/bank/1/suggestions").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"orderId":1002,"customer":"Петро Мельник","status":"Виконано","reasons":%d}]
                """.formatted(MatchReason.TOTAL | MatchReason.NAME));

        assertThat(put("/payments/bank/1/order", "{\"orderId\":1002}")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"orderId\":1002,\"orderCustomer\":\"Петро Мельник\"}");
        assertThat(mvc.get().uri("/orders/1002/payments").session(olena)).bodyJson().isLenientlyEqualTo("[{\"id\":1}]");
        assertThat(put("/payments/bank/1/order", "{\"orderId\":999}")).hasStatus(404);

        assertThat(put("/payments/bank/1/visible?visible=false", null)).hasStatus(204);
        assertThat(mvc.get().uri("/payments/bank").session(olena)).bodyJson().isLenientlyEqualTo("{\"items\":[{\"id\":2}]}");
        assertThat(mvc.get().uri("/payments/bank?hidden=true").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"items\":[{\"id\":1,\"isVisible\":false}]}");
    }

    @Test
    void importWithoutBankKeysReportsNotConfigured() {
        assertThat(mvc.post().uri("/payments/bank/import").session(olena)).hasStatusOk().bodyJson()
                .isStrictlyEqualTo("{\"clientConfigured\":false,\"fetched\":0,\"added\":0,\"skipped\":0}");
    }

    @Test
    void liqPayWithoutKeysGivesATestLinkAndNotPaid() {
        MvcTestResult link = post("/orders/1002/liqpay-link", "{\"amount\":500}");
        assertThat(link).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"amount\":500,\"currency\":\"UAH\",\"isTest\":true}");
        assertThat(link).bodyText().contains("https://www.liqpay.ua/api/3/checkout?data=");

        assertThat(mvc.get().uri("/orders/1002/liqpay-status").session(olena)).hasStatusOk().bodyJson()
                .extractingPath("$.state").isEqualTo(LiqPayState.NOT_FOUND.ordinal());

        assertThat(post("/orders/1002/liqpay-link", "{\"amount\":800.01}")).hasStatus(400);
        assertThat(post("/orders/999/liqpay-link", "{\"amount\":null}")).hasStatus(404);
        assertThat(mvc.get().uri("/orders/1002/liqpay-status").session(login("keeper"))).hasStatus(403);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(String uri, String json) {
        var request = mvc.put().uri(uri).session(olena);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }
}
