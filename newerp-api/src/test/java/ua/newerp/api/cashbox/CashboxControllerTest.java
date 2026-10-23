package ua.newerp.api.cashbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

/** Without cashier keys the stub client is used; fake-receipts lets it "fiscalize" test receipts, as in local development. */
@TestPropertySource(properties = "checkbox.fake-receipts=true")
@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/cashbox.sql"})
class CashboxControllerTest extends IntegrationTest {

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void shiftReceiptAndZReportForAWholeDay() {
        assertThat(mvc.get().uri("/cashbox/setup").session(olena)).bodyJson().isLenientlyEqualTo("""
                {"fakeMode":true,"cashiers":[{"id":2,"name":"Марта (НП)","autoCloseTime":"21:00:00","canFiscalize":true,"openShiftId":null},
                                             {"id":1,"name":"Олена"}]}
                """);

        // Without an open shift no receipt is issued, and no row is left
        assertThat(post("/orders/1001/cheque/", "{}")).hasStatus(400).bodyText().contains("Спершу відкрийте зміну");
        assertThat(mvc.get().uri("/orders/1001/cheque").session(olena)).hasStatus(204);

        MvcTestResult opened = post("/cashbox/cashiers/2/shift/open", null);
        assertThat(opened).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"cashierId\":2,\"cashier\":\"Марта (НП)\",\"dateEnd\":null}");
        int shiftId = id(opened);

        // The cashier is picked by the payment code (cod), the sum is minus the prepayment
        MvcTestResult issued = post("/orders/1001/cheque/", "{}");
        assertThat(issued).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"orderId":1001,"customer":"Ірина Шевчук","cashierId":2,"sum":1200.0000,"isFiscalized":true,"receiptUrl":null,
                 "workShiftId":%d,"reportId":0}
                """.formatted(shiftId));
        int chequeId = id(issued);
        // A second call returns the same receipt
        assertThat(post("/orders/1001/cheque", "{}")).bodyJson().extractingPath("$.id").isEqualTo(chequeId);

        assertThat(post("/cashbox/cashiers/2/x-report", null)).hasStatusOk().bodyText().contains("ТЕСТОВИЙ");
        assertThat(mvc.get().uri("/cashbox/shifts").session(olena)).bodyJson()
                .isLenientlyEqualTo("[{\"id\":%d,\"cheques\":1,\"chequesSum\":1200.0000,\"openedBy\":\"Олена Коваль\"}]".formatted(shiftId));

        MvcTestResult closed = post("/cashbox/cashiers/2/shift/close", null);
        assertThat(closed).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"cashierId\":2,\"closedBy\":\"Олена Коваль\"}");
        int reportId = id(closed);

        assertThat(mvc.get().uri("/cashbox/cheques?fiscalized=true").session(olena)).bodyJson().isLenientlyEqualTo("""
                {"items":[{"id":%d,"reportId":%d}],"page":1,"pageSize":50,"totalCount":1}
                """.formatted(chequeId, reportId));
        assertThat(mvc.get().uri("/cashbox/shifts").session(olena)).bodyJson().extractingPath("$[0].dateEnd").isNotNull();
        assertThat(mvc.get().uri("/cashbox/reports").session(olena)).bodyJson().extractingPath("$[0].id").isEqualTo(reportId);

        // A fiscalized receipt cannot be deleted
        assertThat(mvc.delete().uri("/cashbox/cheques/" + chequeId).session(olena)).hasStatus(400);
    }

    @Test
    void receiptNeedsProductsWithAPrice() {
        post("/cashbox/cashiers/1/shift/open", null);

        assertThat(post("/orders/1002/cheque", "{}")).hasStatus(400).bodyText().contains("немає товарів з ціною");
        assertThat(post("/orders/9999/cheque", "{}")).hasStatus(404);
    }

    @Test
    void secondShiftOfTheSameCashierIsRefused() {
        assertThat(post("/cashbox/cashiers/1/shift/open", null)).hasStatusOk();
        assertThat(post("/cashbox/cashiers/1/shift/open", null)).hasStatus(400).bodyText().contains("уже відкрита зміна");
    }

    @Test
    void setupIsEditedOnlyByAdminAndAWarehouseInUseIsKept() {
        assertThat(mvc.post().uri("/cashbox/warehouses").session(olena).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Склад\"}")).hasStatus(403);

        MockHttpSession admin = login("admin");
        MvcTestResult created = mvc.post().uri("/cashbox/warehouses").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Магазин на Подолі\",\"description\":\"вул. Сагайдачного\"}").exchange();
        int warehouseId = id(created);
        assertThat(mvc.post().uri("/cashbox/cashiers").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Ігор","paymentCode":"cod","autoCloseTime":"21:30:00","warehouseId":%d,"requisitesId":0,"isActive":true}
                        """.formatted(warehouseId))).hasStatusOk();
        assertThat(mvc.put().uri("/cashbox/cashiers/1").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Олена\",\"warehouseId\":999,\"isActive\":true}")).hasStatus(400);

        assertThat(mvc.delete().uri("/cashbox/warehouses/" + warehouseId).session(admin)).hasStatus(400);
        assertThat(mvc.get().uri("/cashbox/setup").session(admin)).bodyJson().isLenientlyEqualTo("""
                {"warehouses":[{"id":%d,"name":"Магазин на Подолі","description":"вул. Сагайдачного"}],
                 "cashiers":[{"name":"Ігор","warehouse":"Магазин на Подолі","autoCloseTime":"21:30:00"},{},{}]}
                """.formatted(warehouseId));

        assertThat(mvc.get().uri("/cashbox/setup").session(login("keeper"))).hasStatus(403);
    }

    private MvcTestResult post(String uri, String json) {
        var request = mvc.post().uri(uri).session(olena);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    private static int id(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
