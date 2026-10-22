package ua.newerp.api.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;

/** Without an API key the stub client is used; fake-documents lets it "create" test waybills, as in local development. */
@TestPropertySource(properties = "nova-poshta.fake-documents=true")
@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/delivery.sql"})
class DeliveryControllerTest extends IntegrationTest {

    static final String CREATE = """
            {"firstName":"Ірина","lastName":"Шевчук","phone":"+38 (067) 111-22-33","cityRef":"city-kyiv","warehouseRef":"wh-kyiv-2",
             "declaredCost":1500,"cashOnDelivery":1500,"senderPays":false,
             "seats":[{"weight":1,"width":31,"length":36,"height":6}]}
            """;

    @Autowired
    JdbcClient jdbc;

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void onlyAdminAndManager() {
        assertThat(mvc.get().uri("/delivery/reminders").session(login("keeper"))).hasStatus(403);
        assertThat(mvc.get().uri("/orders/1001/shipment").session(login("keeper"))).hasStatus(403);
        assertThat(mvc.get().uri("/delivery/reminders").session(login("admin"))).hasStatusOk();
    }

    @Test
    void formIsFilledFromTheOrderAndDirectory() {
        assertThat(mvc.get().uri("/orders/1001/shipment/form").session(olena)).bodyJson().isLenientlyEqualTo("""
                {"firstName":"Ірина","lastName":"Шевчук","phone":"+380671112233","cityName":"Київ",
                 "city":{"ref":"city-kyiv","name":"Київ"},
                 "warehouseName":"Відділення №2: вул. Хрещатик, 1",
                 "warehouse":{"ref":"wh-kyiv-2","name":"Відділення №2: вул. Хрещатик, 1","category":"Branch","maxWeight":1000},
                 "declaredCost":1500.0000,"cashOnDelivery":1500.0000,
                 "senders":[{"id":"test","isDefault":true}],"dropoffPoints":[{"id":"test"}],
                 "seatPresets":[{"name":"Маленька","seat":{"weight":1,"width":31,"length":36,"height":6}},{"name":"Середня"},{"name":"Велика"}],
                 "canCreate":true,"testMode":true}
                """);
        assertThat(mvc.get().uri("/orders/1002/shipment/form").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"city\":null,\"warehouse\":null,\"cashOnDelivery\":0}");
        assertThat(mvc.get().uri("/orders/9999/shipment/form").session(olena)).hasStatus(404);
    }

    @Test
    void createPrintDeleteTestWaybill() {
        assertThat(mvc.get().uri("/orders/1001/shipment").session(olena)).hasStatus(204);

        assertThat(post("/orders/1001/shipment", CREATE.replace("wh-kyiv-2", "wh-lviv-1")))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Такого відділення в цьому місті немає в довіднику.");
        assertThat(post("/orders/1001/shipment", CREATE.replace("\"seats\":[{\"weight\":1,", "\"seats\":[{\"weight\":0,")))
                .hasStatus(400);

        assertThat(post("/orders/1001/shipment", CREATE)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"orderId":1001,"carrier":"novaposhta","cost":0.0000,"status":"","statusCode":0,"tone":0,"isFinal":false}
                """);
        String number = jdbc.sql("SELECT number FROM dd_novaposhta_document WHERE order_id = 1001").query(String.class).single();
        assertThat(number).hasSize(14).startsWith("00");
        assertThat(jdbc.sql("SELECT sender_id FROM erp_shipment_sender").query(String.class).single()).isEqualTo("test");
        assertThat(mvc.get().uri("/orders/1001/shipment").session(olena)).bodyJson().extractingPath("$.number").isEqualTo(number);

        assertThat(post("/orders/1001/shipment", CREATE)).hasStatus(400).bodyJson().extractingPath("$.detail")
                .isEqualTo("За замовленням уже є ТТН. Щоб створити нову, спершу видаліть стару.");
        assertThat(mvc.get().uri("/orders/1001/shipment/print?kind=markings").session(olena))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Друк недоступний: ключ API Нової Пошти не задано.");

        assertThat(mvc.delete().uri("/orders/1001/shipment").session(olena)).hasStatus(204);
        assertThat(mvc.get().uri("/orders/1001/shipment").session(olena)).hasStatus(204);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM erp_shipment_sender").query(Integer.class).single()).isZero();
        assertThat(mvc.delete().uri("/orders/1001/shipment").session(olena)).hasStatus(404);
    }

    @Test
    void finalWaybillCannotBeDeleted() {
        jdbc.sql("UPDATE dd_novaposhta_document SET status_code = 9, redelivery = 1 WHERE order_id = 1002").update();
        assertThat(mvc.get().uri("/orders/1002/shipment").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"number\":\"20450000000002\",\"statusCode\":9,\"isFinal\":true,\"tone\":0,\"redelivery\":true}");
        assertThat(mvc.delete().uri("/orders/1002/shipment").session(olena)).hasStatus(400);
    }

    @Test
    void remindersFromTodayToThreeDays() {
        LocalDate today = LocalDate.now();
        jdbc.sql("UPDATE dd_novaposhta_document SET status_code = 7, status = 'Прибув', date_payed_keeping = :d WHERE order_id = 1002")
                .param("d", today.plusDays(2)).update();
        jdbc.sql("INSERT INTO dd_novaposhta_document (order_id, number, status_code, date_payed_keeping) VALUES (1001, '20450000000001', 8, :d)")
                .param("d", today.plusDays(5)).update();

        assertThat(mvc.get().uri("/delivery/reminders").session(olena)).bodyJson().isLenientlyEqualTo("""
                [{"orderId":1002,"customer":"Петро Мельник","telephone":"+380503334455","number":"20450000000002",
                  "status":"Прибув","datePayedKeeping":"%s","managerName":null}]
                """.formatted(today.plusDays(2)));

        assertThat(mvc.post().uri("/delivery/reminders/1002/done").session(olena)).hasStatus(204);
        assertThat(mvc.get().uri("/delivery/reminders").session(olena)).bodyJson().isEqualTo("[]");
        assertThat(mvc.post().uri("/delivery/reminders/9999/done").session(olena)).hasStatus(404);
    }

    @Test
    void searchesTheDirectory() {
        assertThat(mvc.get().uri("/delivery/cities?search=Київ").session(olena)).bodyJson().isEqualTo("""
                [{"ref":"city-kyiv","name":"Київ"},{"ref":"city-kyivska","name":"Київська Слобода"}]
                """);
        assertThat(mvc.get().uri("/delivery/cities?search=%25").session(olena)).bodyJson().isEqualTo("[]");
        assertThat(mvc.get().uri("/delivery/cities").session(olena)).bodyJson().isEqualTo("[]");
        assertThat(mvc.get().uri("/delivery/warehouses?cityRef=city-kyiv").session(olena)).bodyJson()
                .extractingPath("$[*].ref").asArray().containsExactly("wh-kyiv-2", "wh-kyiv-10");
        assertThat(mvc.get().uri("/delivery/warehouses?cityRef=city-kyiv&search=хрещатик").session(olena)).bodyJson()
                .extractingPath("$[*].ref").asArray().containsExactly("wh-kyiv-2");
    }

    @Test
    void withoutKeyNothingIsAsked() {
        assertThat(mvc.post().uri("/delivery/tracking/refresh").session(olena)).bodyJson()
                .isEqualTo("{\"clientConfigured\":false,\"checked\":0,\"updated\":0}");
        assertThat(mvc.post().uri("/delivery/warehouses/refresh").session(olena)).bodyJson()
                .isEqualTo("{\"clientConfigured\":false,\"count\":0}");
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
