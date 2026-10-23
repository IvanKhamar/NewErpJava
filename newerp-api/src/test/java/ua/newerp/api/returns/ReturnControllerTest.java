package ua.newerp.api.returns;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/returns.sql"})
class ReturnControllerTest extends IntegrationTest {

    @Test
    void returnFromOrderLineWithHistoryAndQuantityLimit() {
        MockHttpSession olena = login("olena");

        assertThat(mvc.get().uri("/returns/lookups").session(olena)).bodyJson().extractingPath("$.statuses[*].name")
                .isEqualTo(List.of("Очікує", "Отримано"));

        // Order 1001 has 2 belts: one goes back.
        MvcTestResult created = post(olena, "/returns",
                "{\"orderId\":1001,\"productId\":51,\"quantity\":1,\"reasonId\":1,\"statusId\":1,\"comment\":\"Мала\"}");
        assertThat(created).hasStatus(201);
        int id = JsonPath.read(body(created), "$.id");

        assertThat(post(olena, "/returns/" + id + "/history", "{\"statusId\":2,\"comment\":\"Посилку отримали\",\"actionId\":1}")).hasStatus(204);
        assertThat(mvc.get().uri("/returns/" + id).session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"orderId":1001,"product":"Пояс","model":"P-02","quantity":1,"statusName":"Отримано","actionName":"Повернено кошти",
                 "reasonName":"Не підійшов розмір","customer":"Ірина Шевчук",
                 "history":[{"comment":"Мала","statusName":"Очікує"},{"comment":"Посилку отримали","statusName":"Отримано"}]}
                """);
        assertThat(mvc.get().uri("/returns/" + id).session(olena)).bodyJson().extractingPath("$.history[*].comment")
                .isEqualTo(List.of("Мала", "Посилку отримали"));

        assertThat(mvc.get().uri("/returns?orderId=1001").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"totalCount\":1,\"items\":[{\"id\":%d}]}".formatted(id));

        // One more belt can go back, a third is not there.
        assertThat(post(olena, "/returns", "{\"orderId\":1001,\"productId\":51,\"quantity\":2,\"reasonId\":1,\"statusId\":1}")).hasStatus(400);
        assertThat(mvc.get().uri("/returns/9999").session(olena)).hasStatus(404);
        assertThat(mvc.get().uri("/returns").session(login("keeper"))).hasStatus(403);
    }

    private MvcTestResult post(MockHttpSession session, String uri, String json) {
        return mvc.post().uri(uri).session(session).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
