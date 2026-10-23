package ua.newerp.api.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

/** Consumables at several warehouses: Бавовна has 50 m in total, all on the main stock at first. */
@Sql(scripts = {"/sql/users.sql", "/sql/stock.sql", "/sql/locations.sql"}, statements = """
        INSERT INTO dd_warehouse (warehouse_id, name, description) VALUES (901, 'Цех', ''), (902, 'Склад на Лесі', '')
        """)
class StockLocationTest extends IntegrationTest {

    MockHttpSession keeper;

    @BeforeEach
    void logIn() {
        keeper = login("keeper");
    }

    @Test
    void movesBetweenWarehousesKeepTheTotal() {
        assertThat(post("/stock/items/2/transfers", "{\"toWarehouseId\":901,\"quantity\":20,\"comment\":\"на тиждень\"}"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"itemId":2,"total":50.000,"places":[{"warehouseId":null,"name":"Основний склад","quantity":30.000},
                                                             {"warehouseId":901,"name":"Цех","quantity":20.000}]}
                        """);
        assertThat(post("/stock/items/2/transfers", "{\"fromWarehouseId\":901,\"toWarehouseId\":902,\"quantity\":5}")).hasStatusOk();
        // more than the workshop has
        assertThat(post("/stock/items/2/transfers", "{\"fromWarehouseId\":901,\"quantity\":16}")).hasStatus(400)
                .bodyJson().extractingPath("$.detail").asString().contains("на цьому складі є 15 м");
        assertThat(post("/stock/items/2/transfers", "{\"toWarehouseId\":999,\"quantity\":1}")).hasStatus(404);

        assertThat(mvc.get().uri("/stock/items/2/places").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"total":50.000,"places":[{"warehouseId":null,"quantity":30.000},{"name":"Склад на Лесі","quantity":5.000},
                                          {"name":"Цех","quantity":15.000}]}
                """);
        assertThat(mvc.get().uri("/stock/items/2/transfers").session(keeper)).bodyJson().isLenientlyEqualTo("""
                [{"fromName":"Цех","toName":"Склад на Лесі","quantity":5.000,"username":"keeper"},
                 {"fromWarehouseId":null,"fromName":"Основний склад","toWarehouseId":901,"quantity":20.000,"comment":"на тиждень"}]
                """);
        assertThat(mvc.get().uri("/stock/items/2").session(keeper)).bodyJson().extractingPath("$.quantity").isEqualTo(50.0);
    }

    @Test
    void useAtAWarehouseChangesTheTotalAndGoesIntoTheLog() {
        post("/stock/items/2/transfers", "{\"toWarehouseId\":901,\"quantity\":20}");

        assertThat(post("/stock/items/2/warehouses/901/adjustments", "{\"delta\":-8,\"reason\":\"Крій\"}")).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("{\"delta\":-8,\"quantityAfter\":42.000,\"reason\":\"Крій · Цех\"}");
        assertThat(post("/stock/items/2/warehouses/901/adjustments", "{\"delta\":-13,\"reason\":\"Крій\"}")).hasStatus(400);
        // without a warehouse it is the main stock: 30 m there, the 12 in the workshop are not taken from here
        assertThat(post("/stock/items/2/adjustments", "{\"delta\":-31,\"reason\":\"Крій\"}")).hasStatus(400)
                .bodyJson().extractingPath("$.detail").asString().contains("на основному складі є 30 м");
        assertThat(post("/stock/items/2/adjustments", "{\"delta\":-30,\"reason\":\"Крій\"}")).hasStatusOk();

        assertThat(mvc.get().uri("/stock/items/2/places").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"total":12.000,"places":[{"warehouseId":null,"quantity":0.000},{"warehouseId":901,"quantity":12.000}]}
                """);
    }

    @Test
    void overviewHasAColumnPerWarehouseThatHoldsStock() {
        post("/stock/items/1/transfers", "{\"toWarehouseId\":902,\"quantity\":1}");
        post("/stock/items/2/transfers", "{\"toWarehouseId\":902,\"quantity\":10}");

        // Льон 200 ₴: 2 + 1; Бавовна 120 ₴: 40 + 10; Ґудзики 1.5 ₴: 20 on the main stock; the workshop holds nothing
        assertThat(mvc.get().uri("/stock/by-warehouse").session(keeper)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"places":[{"warehouseId":null,"name":"Основний склад","value":5230.00},
                           {"warehouseId":902,"name":"Склад на Лесі","value":1400.00}],
                 "rows":[{"itemId":1,"total":3.000,"quantities":[2.000,1.000]},
                         {"itemId":2,"total":50.000,"quantities":[40.000,10.000]},
                         {"itemId":3,"quantities":[20.000,0]},
                         {"itemId":4,"quantities":[0.000,0]}]}
                """);
    }

    @Test
    void anItemOrAWarehouseWithStockSomewhereCannotGo() {
        post("/stock/items/4/warehouses/901/adjustments", "{\"delta\":3,\"reason\":\"Прихід\"}");

        // Нитки: 3 in the workshop, so the total is 3 too: archiving is refused either way; a warehouse with stock cannot go
        assertThat(mvc.post().uri("/stock/items/4/archive").session(keeper)).hasStatus(400);
        assertThat(mvc.delete().uri("/locations/901").session(login("admin"))).hasStatus(400)
                .bodyJson().extractingPath("$.detail").asString().contains("перемістіть");
        assertThat(mvc.delete().uri("/locations/902").session(login("admin"))).hasStatus(204);
        assertThat(mvc.get().uri("/stock/by-warehouse").session(login("olena"))).hasStatus(403);
    }

    @Test
    void aDeliveryCanGoStraightToAWarehouse() throws Exception {
        MvcTestResult created = post("/stock/purchase-orders", "{\"supplierId\":1,\"lines\":[{\"itemId\":1,\"quantity\":10,\"price\":200}]}");
        int id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        assertThat(mvc.post().uri("/stock/purchase-orders/" + id + "/ordered").session(keeper)).hasStatus(204);
        int lineId = JsonPath.read(mvc.get().uri("/stock/purchase-orders/" + id).session(keeper).exchange().getResponse()
                .getContentAsString(StandardCharsets.UTF_8), "$.lines[0].id");

        assertThat(post("/stock/purchase-orders/" + id + "/receive", "{\"lines\":[{\"lineId\":" + lineId + ",\"quantity\":4}],"
                + "\"warehouseId\":999}")).hasStatus(404);
        assertThat(post("/stock/purchase-orders/" + id + "/receive", "{\"lines\":[{\"lineId\":" + lineId + ",\"quantity\":4}],"
                + "\"warehouseId\":901}")).hasStatus(204);

        // Льон: 3 before + 4 delivered to the workshop; the main stock keeps its 3
        assertThat(mvc.get().uri("/stock/items/1/places").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"total":7.000,"places":[{"warehouseId":null,"quantity":3.000},{"warehouseId":901,"quantity":4.000}]}
                """);
    }

    @Test
    void stocktakingOfAWarehouseCountsOnlyWhatLiesThere() {
        post("/stock/items/2/transfers", "{\"toWarehouseId\":901,\"quantity\":20}");

        // the workshop has 20 m of Бавовна in the books, 17 were counted; Льон was not there at all, 1 m was found
        assertThat(post("/stock/stocktaking", """
                {"warehouseId":901,"comment":"цех","counts":[{"itemId":2,"actual":17},{"itemId":1,"actual":1}]}
                """)).hasStatusOk().bodyJson().isLenientlyEqualTo("{\"counted\":2,\"changed\":2,\"value\":-160.00}");

        assertThat(mvc.get().uri("/stock/items/2/places").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"total":47.000,"places":[{"warehouseId":null,"quantity":30.000},{"warehouseId":901,"quantity":17.000}]}
                """);
        assertThat(mvc.get().uri("/stock/items/1/places").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"total":4.000,"places":[{"warehouseId":null,"quantity":3.000},{"warehouseId":901,"quantity":1.000}]}
                """);
        // the main stock is counted without a warehouse: 30 m of Бавовна there, not the 47 in total
        assertThat(post("/stock/stocktaking", "{\"counts\":[{\"itemId\":2,\"actual\":30}]}")).bodyJson()
                .extractingPath("$.changed").isEqualTo(0);
        assertThat(post("/stock/stocktaking", "{\"warehouseId\":999,\"counts\":[{\"itemId\":2,\"actual\":1}]}")).hasStatus(404);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(keeper).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
