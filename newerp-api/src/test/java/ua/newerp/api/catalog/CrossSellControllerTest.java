package ua.newerp.api.catalog;

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

@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/product-sort.sql", "/sql/crosssell.sql"})
class CrossSellControllerTest extends IntegrationTest {

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void addsCategoriesAndProductsChangesDiscountAndRemoves() {
        assertThat(post("/catalog/crosssell/items", "{\"type\":\"category\",\"targetId\":30,\"discount\":10}")).hasStatus(201);
        MvcTestResult product = post("/catalog/crosssell/items", "{\"type\":\"Product\",\"targetId\":201}");
        assertThat(product).hasStatus(201).bodyJson().isLenientlyEqualTo("""
                {"type":"product","targetId":201,"name":"Сукня 201","discount":0,"products":1}
                """);
        assertThat(post("/catalog/crosssell/items", "{\"type\":\"product\",\"targetId\":201}")).hasStatus(400); // already there
        assertThat(post("/catalog/crosssell/items", "{\"type\":\"product\",\"targetId\":999}")).hasStatus(404);
        assertThat(post("/catalog/crosssell/items", "{\"type\":\"label\",\"targetId\":1}")).hasStatus(400);

        assertThat(mvc.get().uri("/catalog/crosssell/items").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"type":"category","name":"Сукні","discount":10.00,"products":3},{"type":"product","name":"Сукня 201"}]
                """);

        int id = JsonPath.read(new String(product.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
        assertThat(mvc.put().uri("/catalog/crosssell/items/" + id + "/discount").session(olena).contentType(MediaType.APPLICATION_JSON)
                .content("{\"discount\":15.5}")).hasStatusOk().bodyJson().extractingPath("$.discount").isEqualTo(15.5);
        assertThat(mvc.put().uri("/catalog/crosssell/items/" + id + "/discount").session(olena).contentType(MediaType.APPLICATION_JSON)
                .content("{\"discount\":100}")).hasStatus(400);
        assertThat(mvc.delete().uri("/catalog/crosssell/items/" + id).session(olena)).hasStatus(204);
        assertThat(mvc.get().uri("/catalog/crosssell/items").session(olena)).bodyJson().extractingPath("$.length()").isEqualTo(1);

        assertThat(mvc.get().uri("/catalog/crosssell/categories").session(olena)).bodyJson().isLenientlyEqualTo("""
                [{"id":30,"name":"Сукні"}]
                """);
        assertThat(mvc.get().uri("/catalog/crosssell/items").session(login("keeper"))).hasStatus(403);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
