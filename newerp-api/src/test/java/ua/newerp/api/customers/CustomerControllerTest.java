package ua.newerp.api.customers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/customers.sql"})
class CustomerControllerTest extends IntegrationTest {

    @Test
    void cardFindsOrdersByPhoneWithRiskAndNotes() {
        MockHttpSession olena = login("olena");

        assertThat(mvc.get().uri("/customers?search=Коваль").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"totalCount":1,"items":[{"id":1,"name":"Олена Коваль","groupName":"Роздріб","orderCount":0}]}
                """);

        // A guest order with the same phone written another way is in the card too.
        assertThat(mvc.post().uri("/customers/1/notes").session(olena).contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\" Просила дзвонити ввечері \"}")).hasStatus(204);
        assertThat(mvc.get().uri("/customers/1").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"orders":[{"id":1050,"byPhone":true,"statusName":"Відмінено"}],
                 "stats":{"orderCount":1,"cancelledCount":1,"spent":0,"riskLevel":"Red"},
                 "addresses":[{"city":"Київ","isDefault":true}],
                 "notes":[{"comment":"Просила дзвонити ввечері"}]}
                """);

        assertThat(mvc.post().uri("/customers/1/notes").session(olena).contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\" \"}")).hasStatus(400);
        assertThat(mvc.get().uri("/customers/9999").session(olena)).hasStatus(404);
        assertThat(mvc.get().uri("/customers").session(login("keeper"))).hasStatus(403);
    }
}
