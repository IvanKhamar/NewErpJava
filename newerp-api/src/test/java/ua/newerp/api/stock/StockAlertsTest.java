package ua.newerp.api.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql"})
class StockAlertsTest extends IntegrationTest {

    @Test
    void itemsAndModelsBelowTheirMinimumWithWhatIsAlreadyOrdered() {
        MockHttpSession keeper = login("keeper");
        // Buttons are ordered from the supplier, so they go last: threads (0 of 5) and linen (3 of 10) are not ordered yet
        assertThat(mvc.post().uri("/stock/purchase-orders").session(keeper).contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":2,\"lines\":[{\"itemId\":3,\"quantity\":200,\"price\":1.5}]}")).hasStatus(201);

        assertThat(mvc.get().uri("/stock/alerts").session(keeper)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"items":[{"itemId":4,"name":"Нитки","quantity":0.000,"minQuantity":5.000,"onOrder":0},
                          {"itemId":1,"name":"Льон білий","unit":"м","onOrder":0},
                          {"itemId":3,"onOrder":200.000}],
                 "notOrdered":2,
                 "models":[{"productionId":1,"name":"Сукня Лілія","minTotal":10,"sewn":4.000,"deficit":6.000}]}
                """);
        assertThat(mvc.get().uri("/stock/alerts").session(keeper)).bodyJson().extractingPath("$.items[*].itemId").isEqualTo(List.of(4, 1, 3));
        assertThat(mvc.get().uri("/stock/alerts").session(login("olena"))).hasStatus(403);
    }
}
