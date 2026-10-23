package ua.newerp.api.production;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;

/** Sewn pieces at several warehouses: "Сукня Лілія" S has 3 sewn, M has 1; the T-shirt L has 8. */
@Sql(scripts = {"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql", "/sql/locations.sql"}, statements = """
        INSERT INTO dd_warehouse (warehouse_id, name, description) VALUES (901, 'Шоурум', ''), (902, 'Пункт на Лесі', '')
        """)
class SewnLocationTest extends IntegrationTest {

    MockHttpSession keeper;

    @BeforeEach
    void logIn() {
        keeper = login("keeper");
    }

    @Test
    void movesPiecesOfASizeBetweenPlaces() {
        assertThat(post("/production/models/1/transfers", "{\"sizeId\":1,\"toWarehouseId\":901,\"quantity\":2,\"comment\":\"вітрина\"}"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"sizeId":1,"name":"S","total":3.000,"places":[{"warehouseId":null,"quantity":1.000},
                                                                      {"warehouseId":901,"name":"Шоурум","quantity":2.000}]},
                         {"sizeId":2,"name":"M","total":1.000,"places":[{"warehouseId":null,"quantity":1.000}]}]
                        """);
        assertThat(post("/production/models/1/transfers", "{\"sizeId\":1,\"fromWarehouseId\":901,\"quantity\":3}")).hasStatus(400)
                .bodyJson().extractingPath("$.detail").asString().contains("на цьому складі пошито 2");
        assertThat(post("/production/models/1/transfers", "{\"sizeId\":3,\"toWarehouseId\":901,\"quantity\":1}")).hasStatus(400);

        assertThat(mvc.get().uri("/production/models/1/transfers").session(keeper)).bodyJson().isLenientlyEqualTo("""
                [{"sizeName":"S","fromName":"Основний склад","toName":"Шоурум","quantity":2.000,"comment":"вітрина"}]
                """);
        assertThat(mvc.get().uri("/production/by-warehouse").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"places":[{"warehouseId":null},{"warehouseId":901,"name":"Шоурум"}],
                 "rows":[{"sizeId":1,"quantities":[1.000,2.000]},{"sizeId":2,"quantities":[1.000,0]},
                         {"sizeId":3,"modelName":"Футболка базова","quantities":[8.000,0]}]}
                """);
    }

    @Test
    void sewnAndShippedAtAWarehouseAndItsStocktaking() {
        assertThat(post("/production/models/2/sewn", "{\"warehouseId\":902,\"quantities\":[{\"sizeId\":3,\"quantity\":4}]}"))
                .hasStatusOk();
        assertThat(post("/production/models/2/ship", "{\"warehouseId\":902,\"quantities\":[{\"sizeId\":3,\"quantity\":5}]}"))
                .hasStatus(400);
        assertThat(post("/production/models/2/ship", "{\"warehouseId\":902,\"quantities\":[{\"sizeId\":3,\"quantity\":1}]}"))
                .hasStatusOk();
        // cutting has no warehouse
        assertThat(post("/production/models/1/cut", "{\"warehouseId\":902,\"quantities\":[{\"sizeId\":1,\"quantity\":1}]}"))
                .hasStatus(400);

        // the point counted 2 T-shirts instead of 3; the main stock keeps its 8
        assertThat(post("/production/models/2/stocktaking", "{\"warehouseId\":902,\"sizes\":[{\"sizeId\":3,\"sewn\":2}]}"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("{\"sewn\":[{\"sizeId\":3,\"before\":11.000,\"after\":10.000}]}");
        assertThat(post("/production/models/2/stocktaking", "{\"warehouseId\":902,\"sizes\":[{\"sizeId\":3,\"cut\":1}]}"))
                .hasStatus(400);
        assertThat(mvc.get().uri("/production/models/2/places").session(keeper)).bodyJson().isLenientlyEqualTo("""
                [{"sizeId":3,"total":10.000,"places":[{"warehouseId":null,"quantity":8.000},{"warehouseId":902,"quantity":2.000}]}]
                """);
        assertThat(mvc.delete().uri("/locations/902").session(login("admin"))).hasStatus(400);
        assertThat(mvc.get().uri("/production/by-warehouse").session(login("olena"))).hasStatus(403);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(keeper).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
