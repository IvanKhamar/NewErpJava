package ua.newerp.api.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql", "/sql/orders.sql", "/sql/catalog.sql", "/sql/sewing-plan.sql"})
class SewingPlanTest extends IntegrationTest {

    MockHttpSession keeper;

    @Test
    void thePlanCountsWhatActiveOrdersMissMinusFreeSewnStockAndWhatOrdersAlreadyHold() {
        keeper = login("keeper");
        int modelId = JsonPath.read(body(send("POST", "/production/models",
                "{\"name\":\"Сорочка\",\"sizes\":[{\"name\":\"S\"},{\"name\":\"M\"}]}")), "$.id");
        String model = body(mvc.get().uri("/production/models/" + modelId).session(keeper).exchange());
        assertThat((String) JsonPath.read(model, "$.sizes[1].name")).isEqualTo("M");
        int sizeM = JsonPath.read(model, "$.sizes[1].id");
        assertThat(send("PUT", "/production/models/%d/links".formatted(modelId),
                "{\"productIds\":[60],\"categories\":[],\"sizeOptions\":[{\"sizeId\":%d,\"optionValueId\":102}]}".formatted(sizeM)))
                .hasStatus(204);
        operation(modelId, "cut", sizeM);

        // Order 1006 wants 2 M, nothing is sewn, 1 is cut: sew 2, 1 of them from the cut stock, cut 1 more.
        // The deadline is the order date 01.10.2026 + 10 days of the category.
        assertThat(mvc.get().uri("/production/plan").session(keeper)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"orderCount":2,
                 "rows":[{"productionId":%d,"sizeName":"M","ordered":2,"missing":2,"toSew":2,"fromCut":1.000,"toCut":1.000,
                          "deadline":"2026-10-11","overdue":true,"orderIds":[1006]}]}
                """.formatted(modelId));
        // Order 1001 has a belt not linked to any model: it shows among the issues
        assertThat(mvc.get().uri("/production/plan").session(keeper)).bodyJson()
                .extractingPath("$.issues[?(@.productName == 'Пояс')].orderIds[0]").asArray().containsExactly(1001);

        // One is sewn and the order is accepted: it takes 1 from the stock and still misses 1
        operation(modelId, "sewn", sizeM);
        assertThat(mvc.put().uri("/orders/1006/status").session(login("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusId\":18}")).hasStatusOk();
        assertThat(mvc.get().uri("/production/plan").session(keeper)).bodyJson()
                .isLenientlyEqualTo("{\"rows\":[{\"ordered\":2,\"missing\":1.000,\"toSew\":1.000,\"sewnStock\":0.000}]}");

        // One more is sewn: the free stock covers the rest, and the "only what to sew" plan has no row
        operation(modelId, "sewn", sizeM);
        assertThat(mvc.get().uri("/production/plan").session(keeper)).bodyJson().extractingPath("$.rows").asArray().isEmpty();
        assertThat(mvc.get().uri("/production/plan?onlyToSew=false").session(keeper)).bodyJson()
                .isLenientlyEqualTo("{\"rows\":[{\"sizeId\":%d,\"toSew\":0}]}".formatted(sizeM));

        assertThat(mvc.get().uri("/production/plan").session(login("olena"))).hasStatus(403);
    }

    private void operation(int modelId, String operation, int sizeId) {
        assertThat(send("POST", "/production/models/%d/%s".formatted(modelId, operation),
                "{\"quantities\":[{\"sizeId\":%d,\"quantity\":1}]}".formatted(sizeId))).hasStatusOk();
    }

    private MvcTestResult send(String method, String uri, String json) {
        var request = method.equals("PUT") ? mvc.put().uri(uri) : mvc.post().uri(uri);
        return request.session(keeper).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
