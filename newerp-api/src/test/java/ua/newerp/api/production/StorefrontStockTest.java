package ua.newerp.api.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

/** Sewn stock on the shop's storefront: the OpenCart quantities of the linked sizes and products. */
@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql", "/sql/catalog.sql"})
class StorefrontStockTest extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    MockHttpSession admin;

    @Test
    void sewnAndShippedPiecesAreShownOnTheStorefront() {
        admin = login("admin");
        int modelId = id(post("/production/models", "{\"name\":\"Сорочка лляна\",\"sizes\":[{\"name\":\"S\"},{\"name\":\"M\"}]}"));
        String model = body(mvc.get().uri("/production/models/" + modelId).session(admin).exchange());
        assertThat((String) JsonPath.read(model, "$.sizes[1].name")).isEqualTo("M");
        int sizeM = JsonPath.read(model, "$.sizes[1].id");

        // Linked through the category of product 60; size M is the option value chosen in that product
        assertThat(mvc.put().uri("/production/models/%d/links".formatted(modelId)).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[],\"categories\":[{\"categoryId\":30,\"nameFilter\":\"\",\"matchExact\":false}],"
                        + "\"sizeOptions\":[{\"sizeId\":%d,\"optionValueId\":102}]}".formatted(sizeM)))
                .hasStatus(204);
        assertThat(storefront()).containsExactly(0, 0); // right after linking: nothing sewn yet

        assertThat(post("/production/models/%d/sewn".formatted(modelId), "{\"quantities\":[{\"sizeId\":%d,\"quantity\":3}]}".formatted(sizeM)))
                .hasStatusOk();
        assertThat(storefront()).containsExactly(3, 3);

        assertThat(post("/production/models/%d/ship".formatted(modelId), "{\"quantities\":[{\"sizeId\":%d,\"quantity\":1}]}".formatted(sizeM)))
                .hasStatusOk();
        assertThat(storefront()).containsExactly(2, 2);

        // Model 1 of production.sql counts too (linked to product 50, which has no sizes in the shop, so no product changes)
        assertThat(post("/production/storefront/sync", null)).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"models\":2,\"products\":1}");
    }

    /** The quantity of size M and of the product. */
    private List<Integer> storefront() {
        return List.of(
                jdbc.sql("SELECT quantity FROM dd_product_option_value WHERE product_option_value_id = 602").query(Integer.class).single(),
                jdbc.sql("SELECT quantity FROM dd_product WHERE product_id = 60").query(Integer.class).single());
    }

    private MvcTestResult post(String uri, String json) {
        var request = mvc.post().uri(uri).session(admin);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    private static int id(MvcTestResult result) {
        return JsonPath.read(body(result), "$.id");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
