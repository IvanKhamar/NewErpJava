package ua.newerp.api.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/stock.sql"})
class PurchaseOrderControllerTest extends IntegrationTest {

    MockHttpSession keeper;

    @BeforeEach
    void logIn() {
        keeper = login("keeper");
    }

    int create(String body) throws Exception {
        var result = mvc.post().uri("/stock/purchase-orders").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content(body).exchange();
        assertThat(result).hasStatus(201);
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    @Test
    void fullCycleDraftOrderedReceivedInParts() throws Exception {
        int id = create("""
                {"supplierId":1,"lines":[{"itemId":1,"quantity":10,"price":200},{"itemId":2,"quantity":5}],"comment":"терміново"}
                """);

        assertThat(mvc.get().uri("/stock/purchase-orders/" + id).session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"supplierName":"ТексМаркет","status":"draft","comment":"терміново","total":2000.0,
                 "lines":[{"itemName":"Бавовна","quantity":5.000,"price":0.0000},{"itemName":"Льон білий","sum":2000.0}],
                 "messageText":"Добрий день! Хочемо замовити:\\n— Бавовна: 5 м\\n— Льон білий: 10 м\\nДякуємо!"}
                """);

        // Lines are replaced while it is a draft
        assertThat(mvc.put().uri("/stock/purchase-orders/" + id + "/lines").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"itemId\":1,\"quantity\":10,\"price\":200}]}")).hasStatus(204);
        assertThat(mvc.post().uri("/stock/purchase-orders/" + id + "/ordered").session(keeper)).hasStatus(204);

        var order = mvc.get().uri("/stock/purchase-orders/" + id).session(keeper).exchange();
        int lineId = JsonPath.read(order.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.lines[0].id");

        assertThat(mvc.post().uri("/stock/purchase-orders/" + id + "/receive").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"lineId\":" + lineId + ",\"quantity\":4}]}")).hasStatus(204);
        assertThat(mvc.get().uri("/stock/items/1").session(keeper)).bodyJson().extractingPath("$.quantity").isEqualTo(7.0);
        assertThat(mvc.get().uri("/stock/purchase-orders?status=ordered").session(keeper)).bodyJson()
                .extractingPath("$[*].id").asArray().containsExactly(id);

        assertThat(mvc.post().uri("/stock/purchase-orders/" + id + "/receive").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"lineId\":" + lineId + ",\"quantity\":6}]}")).hasStatus(204);
        assertThat(mvc.get().uri("/stock/purchase-orders/" + id).session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"status":"received","lines":[{"received":10.000,"remaining":0.000}]}
                """);
        assertThat(mvc.get().uri("/stock/items/1/movements").session(keeper)).bodyJson()
                .extractingPath("$[0].reason").isEqualTo("Прихід за замовленням постачальнику №" + id);

        assertThat(mvc.post().uri("/stock/purchase-orders/" + id + "/cancel").session(keeper))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Замовлення №" + id + " вже закрите.");
    }

    @Test
    void fromReorderSubtractsWhatIsAlreadyOrdered() throws Exception {
        var result = mvc.post().uri("/stock/purchase-orders/from-reorder/1").session(keeper).exchange();
        assertThat(result).hasStatus(201);
        int id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        assertThat(mvc.get().uri("/stock/purchase-orders/" + id).session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"comment":"Зі звіту «що дозамовити»","lines":[{"itemId":1,"quantity":17.000,"price":200.0000}]}
                """);

        // Already on order, so the supplier has nothing left to reorder
        assertThat(mvc.post().uri("/stock/purchase-orders/from-reorder/1").session(keeper))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("У постачальника 1 зараз нічого дозамовляти.");
    }

    @Test
    void validation() throws Exception {
        assertThat(mvc.post().uri("/stock/purchase-orders").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":9,\"lines\":[{\"itemId\":1,\"quantity\":1}]}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Постачальника 9 не існує.");
        assertThat(mvc.post().uri("/stock/purchase-orders").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":1,\"lines\":[{\"itemId\":5,\"quantity\":1},{\"itemId\":42,\"quantity\":1}]}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Розхідників 5, 42 немає на складі.");
        assertThat(mvc.get().uri("/stock/purchase-orders/999").session(keeper)).hasStatus(404);
        assertThat(mvc.post().uri("/stock/purchase-orders/999/ordered").session(keeper)).hasStatus(404);

        int id = create("{\"supplierId\":1,\"lines\":[{\"itemId\":1,\"quantity\":1}]}");
        assertThat(mvc.post().uri("/stock/purchase-orders/" + id + "/receive").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[]}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail")
                .isEqualTo("Замовлення №" + id + " у статусі draft: приймати товар не можна.");
    }
}
