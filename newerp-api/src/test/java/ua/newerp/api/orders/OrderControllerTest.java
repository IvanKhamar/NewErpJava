package ua.newerp.api.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import ua.newerp.IntegrationTest;

/** Real HTTP requests through the whole app to MySQL with invented orders from sql/orders.sql. */
@Sql("/sql/orders.sql")
class OrderControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void listHidesUnfinishedAndSortsNewestFirst() {
        assertThat(mvc.get().uri("/orders"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"page":1,"pageSize":20,"totalCount":3,"totalPages":1,
                         "items":[{"id":1003},{"id":1002},
                                  {"id":1001,"dateAdded":"2026-10-01T10:00:00","customer":"Ірина Шевчук",
                                   "total":1500.0000,"currencyCode":"UAH","statusId":1,"statusName":"Нове",
                                   "sourceName":"Сайт","managerId":7,"managerName":"Олена Коваль"}]}
                        """);
    }

    @Test
    void filtersAndPaging() {
        assertThat(mvc.get().uri("/orders?statusId=5&pageSize=1&page=2"))
                .bodyJson().isLenientlyEqualTo("""
                        {"page":2,"pageSize":1,"totalCount":2,"totalPages":2,"items":[{"id":1002}]}
                        """);
        assertThat(mvc.get().uri("/orders?sourceId=2")).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(1002);
        assertThat(mvc.get().uri("/orders?managerId=7")).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(1001);
        assertThat(mvc.get().uri("/orders?from=2026-10-05&to=2026-10-05")).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(1002);
    }

    @Test
    void search() {
        assertThat(mvc.get().uri("/orders?search=Мельник")).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(1002);
        assertThat(mvc.get().uri("/orders?search=1001")).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(1001);
        assertThat(mvc.get().uri("/orders?search=20450000000002")).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(1002);
        // % is a literal here, not a LIKE wildcard
        assertThat(mvc.get().uri("/orders?search=50%")).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(1003);
    }

    @Test
    void card() {
        assertThat(mvc.get().uri("/orders/1001"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id":1001,"customer":"Ірина Шевчук","shippingAddress":"вул. Садова, 1","shippingCity":"Київ",
                         "comment":"Подзвонити","statusName":"Нове","sourceId":1,"sourceName":"Сайт",
                         "managerId":7,"managerName":"Олена Коваль",
                         "products":[{"productId":50,"name":"Сукня лляна","quantity":1},{"productId":51,"total":500.0000}],
                         "statusHistory":[{"changedAt":"2026-10-01T10:05:00","oldStatusId":0,"newStatusId":1,
                                           "newStatusName":"Нове","userId":7,"userName":"Олена Коваль"}]}
                        """);
        assertThat(mvc.get().uri("/orders/999")).hasStatus(404);
    }

    @Test
    void directories() {
        assertThat(mvc.get().uri("/orders/statuses")).bodyJson().isEqualTo("""
                [{"id":1,"name":"Нове","color":"#2196f3"},{"id":5,"name":"Виконано","color":"#4caf50"}]
                """);
        assertThat(mvc.get().uri("/orders/sources")).bodyJson().isEqualTo("""
                [{"id":1,"name":"Сайт"},{"id":2,"name":"Instagram"}]
                """);
        // Only active users, sorted by Ukrainian alphabet
        assertThat(mvc.get().uri("/orders/managers")).bodyJson().isEqualTo("""
                [{"id":8,"name":"Андрій Бондар"},{"id":7,"name":"Олена Коваль"}]
                """);
    }
}
