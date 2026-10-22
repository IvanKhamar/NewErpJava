package ua.newerp.api.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql", "/sql/orders.sql", "/sql/order-sewn.sql"})
class OrderActionsTest extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void changeStatusWritesLog() {
        assertThat(status(1001, 5)).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"warnings\":[]}");
        assertThat(mvc.get().uri("/orders/1001").session(olena)).bodyJson().isLenientlyEqualTo("""
                {"statusId":5,"statusName":"Виконано","statusHistory":[{"newStatusId":1},{"oldStatusId":1,"newStatusId":5,"userId":7}]}
                """);

        assertThat(status(1001, 5)).hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Замовлення вже має цей статус.");
        assertThat(status(1001, 77)).hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Статусу 77 не існує.");
        assertThat(status(9999, 5)).hasStatus(404);
        assertThat(mvc.put().uri("/orders/1001/status").session(login("keeper")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusId\":5}")).hasStatus(403);
    }

    @Test
    void assignManager() {
        assertThat(manager(1001, 8)).hasStatus(204);
        assertThat(mvc.get().uri("/orders/1001").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"managerId\":8,\"managerName\":\"Андрій Бондар\"}");
        assertThat(manager(1002, 7)).hasStatus(204);
        assertThat(manager(1001, 9)).hasStatus(400).bodyJson().extractingPath("$.detail")
                .isEqualTo("Користувача 9 не знайдено або його вимкнено.");
        assertThat(manager(9999, 7)).hasStatus(404);
    }

    @Test
    void sewnPiecesFollowTheOrderStatus() {
        // Size S has 3 sewn; ship one, so the order (1 dress + 2 belts of size S) is one short
        assertThat(mvc.post().uri("/production/models/1/ship").session(login("keeper")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantities\":[{\"sizeId\":1,\"quantity\":1}]}")).hasStatusOk();

        assertThat(status(1001, 20)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"warnings":["«Футболка»: розмір із замовлення не зв'язаний з розмірами моделі «Футболка базова», склад не змінено.",
                             "Пошитих «Сукня Лілія» розміру S бракує 1 шт.: списано 2 з 3."]}
                """);
        assertThat(sewnS()).isEqualTo("0.000");

        // Moving inside the write-off statuses changes nothing
        assertThat(status(1001, 22)).hasStatusOk();
        assertThat(sewnS()).isEqualTo("0.000");

        // Leaving them returns only what the order really took (2, not 3)
        assertThat(status(1001, 1)).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"warnings\":[\"«Футболка»: розмір із замовлення не зв'язаний з розмірами моделі «Футболка базова», склад не змінено.\"]}");
        assertThat(sewnS()).isEqualTo("2.000");

        assertThat(mvc.get().uri("/production/log?productionId=1&stage=sewn").session(login("keeper"))).bodyJson().isLenientlyEqualTo("""
                [{"direction":"in","quantity":2.000,"comment":"Скасування #1001","username":"system","userId":7},
                 {"direction":"out","quantity":2.000,"comment":"Замовлення #1001","username":"system"},
                 {"direction":"out","quantity":1.000,"username":"keeper"}]
                """);
    }

    @Test
    void smsChecksTheWaybillNumber() {
        assertThat(sms(1001, "{\"text\":\"Ваша ТТН 20450000000002\"}")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"sent":false,"message":null,"ttnMismatch":{"ttn":"20450000000002","ttnOrderId":1002}}
                """);
        assertThat(mvc.get().uri("/orders/1001/sms").session(olena)).bodyJson().isEqualTo("[]");

        assertThat(sms(1001, "{\"text\":\" Ваша ТТН 20450000000002 \",\"force\":true}")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"sent":false,"ttnMismatch":null,
                 "message":{"orderId":1001,"phone":"380671112233","text":"Ваша ТТН 20450000000002",
                            "response":"НЕ НАДІСЛАНО: заглушка NewErp, справжній SMS-сервіс не підключено"}}
                """);
        assertThat(sms(1002, "{\"text\":\"Ваша ТТН 20450000000002\"}")).hasStatusOk().bodyJson()
                .extractingPath("$.message.phone").isEqualTo("380503334455");
        assertThat(mvc.get().uri("/orders/1001/sms").session(olena)).bodyJson()
                .extractingPath("$[*].text").asArray().containsExactly("Ваша ТТН 20450000000002");

        assertThat(sms(1001, "{\"text\":\"  \"}")).hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Текст SMS порожній.");
        assertThat(sms(9999, "{\"text\":\"Привіт\"}")).hasStatus(404);
    }

    @Test
    void bonusesForAdminOnly() {
        assertThat(status(1001, 22)).hasStatusOk();
        assertThat(mvc.get().uri("/staff/bonuses?from=2026-10-01&to=2026-10-31").session(login("admin"))).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                {"from":"2026-10-01","to":"2026-10-31","percent":1.0,"totalBonus":15.00,
                 "managers":[{"managerId":7,"managerName":"Олена Коваль","ordersCount":1,"revenue":1500.0000,"bonus":15.00}]}
                """);
        assertThat(mvc.get().uri("/staff/bonuses?from=2026-10-02&to=2026-10-31").session(login("admin"))).bodyJson()
                .isLenientlyEqualTo("{\"managers\":[],\"totalBonus\":0}");
        assertThat(mvc.get().uri("/staff/bonuses?from=2026-10-31&to=2026-10-01").session(login("admin"))).hasStatus(400);
        assertThat(mvc.get().uri("/staff/bonuses?from=2026-10-01").session(login("admin"))).hasStatus(400);
        assertThat(mvc.get().uri("/staff/bonuses?from=2026-10-01&to=2026-10-31").session(olena)).hasStatus(403);
    }

    private String sewnS() {
        return jdbc.sql("SELECT sewn_stock FROM dd_stock_production_sizes WHERE size_id = 1").query(String.class).single();
    }

    private MvcTestResult status(int orderId, int statusId) {
        return put("/orders/" + orderId + "/status", "{\"statusId\":" + statusId + "}");
    }

    private MvcTestResult manager(int orderId, int managerId) {
        return put("/orders/" + orderId + "/manager", "{\"managerId\":" + managerId + "}");
    }

    private MvcTestResult sms(int orderId, String json) {
        return mvc.post().uri("/orders/" + orderId + "/sms").session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri).session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
