package ua.newerp.api.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;

/** In orders.sql order 1002 is in status 5, which counts as paid here. Without an SMS key the stub "sends" nothing. */
@TestPropertySource(properties = "vouchers.paid-status-ids=5")
@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/vouchers.sql"})
class VoucherControllerTest extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void listAndCardWithBalanceUsabilityAndHistory() {
        assertThat(mvc.get().uri("/marketing/vouchers").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"id":4,"code":"OFF200","usable":false},{"id":3,"code":"SHOP700","usable":true},
                 {"id":2,"code":"SHOP500","usable":false},{"id":1,"code":"GIFT1000","balance":650.0000,"usable":true}]""");
        assertThat(mvc.get().uri("/marketing/vouchers?search=олена").session(olena)).bodyJson().extractingPath("$[*].id").isEqualTo(List.of(1));

        assertThat(mvc.get().uri("/marketing/vouchers/1").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"code":"GIFT1000","themeName":"Свято","amount":1000.0000,"balance":650.0000,
                 "history":[{"orderId":1001,"customer":"Ірина Шевчук","amount":-350.0000}]}""");
        assertThat(mvc.get().uri("/marketing/vouchers/99").session(olena)).hasStatus(404);
        assertThat(mvc.get().uri("/marketing/vouchers/themes").session(olena)).bodyJson().isLenientlyEqualTo("[{\"id\":1,\"name\":\"Свято\"}]");
    }

    @Test
    void createGeneratesACodeAndRefusesDuplicatesAndBadAmounts() {
        MvcTestResult created = save(mvc.post().uri("/marketing/vouchers"), "{\"toName\":\"Софія\",\"amount\":1500}");
        assertThat(created).hasStatusOk().bodyJson().extractingPath("$.code").asString().matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(created).bodyJson().isLenientlyEqualTo("{\"orderId\":0,\"balance\":1500.0000,\"active\":true,\"usable\":true}");

        assertThat(save(mvc.post().uri("/marketing/vouchers"), "{\"code\":\"gift1000\",\"toName\":\"Софія\",\"amount\":100}")).hasStatus(400);
        assertThat(save(mvc.post().uri("/marketing/vouchers"), "{\"toName\":\"Софія\",\"amount\":0}")).hasStatus(400);

        // Raising the amount of a voucher keeps its history: the balance follows.
        assertThat(save(mvc.put().uri("/marketing/vouchers/1"), "{\"code\":\"GIFT1000\",\"toName\":\"Олена\",\"amount\":1200}")).hasStatusOk()
                .bodyJson().extractingPath("$.balance").isEqualTo(850.0);
    }

    @Test
    void onlyAnUnspentVoucherIsDeletedAndOnlyAUsableOneIsTexted() {
        assertThat(mvc.delete().uri("/marketing/vouchers/1").session(olena)).hasStatus(400);
        assertThat(mvc.delete().uri("/marketing/vouchers/4").session(olena)).hasStatus(204);

        assertThat(save(mvc.post().uri("/marketing/vouchers/1/sms"), "{\"phone\":\"+38 (067) 111-22-33\"}")).hasStatusOk();
        assertThat(jdbc.sql("SELECT phone, text FROM dd_sms_log").query().singleRow())
                .containsEntry("phone", "380671112233")
                .hasEntrySatisfying("text", text -> assertThat((String) text).contains("650").contains("GIFT1000"));
        assertThat(save(mvc.post().uri("/marketing/vouchers/2/sms"), "{\"phone\":\"380671112233\"}")).hasStatus(400); // not paid yet
    }

    @Test
    void storekeeperHasNoAccess() {
        assertThat(mvc.get().uri("/marketing/vouchers").session(login("keeper"))).hasStatus(403);
    }

    private MvcTestResult save(MockMvcRequestBuilder request, String json) {
        return request.session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
