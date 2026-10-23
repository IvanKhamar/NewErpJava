package ua.newerp.api.catalog;

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
import ua.newerp.domain.catalog.SizeAvailability;

/** Pre-order quotas of sizes (size M of product 60 is product_option_value 602). */
@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql", "/sql/catalog.sql"})
class PreOrderControllerTest extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void theQuotaIsEditableAndWhatWasSoldAheadIsSettledAlsoBeforeAStorefrontSync() {
        MockHttpSession olena = login("olena");
        assertThat(put(olena, "/preorders/602", "{\"quota\":5,\"dateAvailable\":\"2026-11-20\"}")).hasStatus(204);
        assertThat(put(olena, "/preorders/602", "{\"quota\":-1}")).hasStatus(400);
        assertThat(put(olena, "/preorders/9999", "{\"quota\":1}")).hasStatus(404);

        // The storefront sold 2 pieces ahead: the stock went negative
        setQuantity(-2);
        assertThat(mvc.get().uri("/preorders?search=лляна&onlyWithQuota=true").session(olena)).bodyJson().isStrictlyEqualTo("""
                [{"productOptionValueId":602,"productId":60,"productName":"Сорочка лляна оверсайз","model":"T-2","size":"M",
                  "quantity":-2,"quota":5,"dateAvailable":"2026-11-20","availability":%d}]
                """.formatted(SizeAvailability.PRE_ORDER.ordinal()));

        assertThat(mvc.post().uri("/preorders/settle").session(olena)).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"sizes\":1}");
        assertThat(size()).containsExactly(0, 3);

        // Passing the sewn stock to the storefront first settles what was sold ahead, otherwise the new stock would overwrite the minus
        setQuantity(-1);
        MockHttpSession admin = login("admin");
        MvcTestResult created = mvc.post().uri("/production/models").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Сорочка лляна\",\"sizes\":[{\"name\":\"M\"}]}").exchange();
        int modelId = JsonPath.read(body(created), "$.id");
        int sizeId = JsonPath.read(body(mvc.get().uri("/production/models/" + modelId).session(admin).exchange()), "$.sizes[0].id");
        assertThat(put(admin, "/production/models/%d/links".formatted(modelId),
                "{\"productIds\":[],\"categories\":[{\"categoryId\":30,\"nameFilter\":\"\",\"matchExact\":false}],"
                        + "\"sizeOptions\":[{\"sizeId\":%d,\"optionValueId\":102}]}".formatted(sizeId)))
                .hasStatus(204);
        assertThat(size()).containsExactly(0, 2);

        assertThat(put(olena, "/preorders/602", "{\"quota\":0,\"dateAvailable\":null}")).hasStatus(204);
        assertThat(mvc.get().uri("/preorders?onlyWithQuota=true").session(olena)).bodyJson().isStrictlyEqualTo("[]");
        assertThat(mvc.get().uri("/preorders?search=лляна").session(olena)).bodyJson()
                .isLenientlyEqualTo("[{\"productOptionValueId\":602,\"dateAvailable\":null}]");

        assertThat(mvc.get().uri("/preorders").session(login("keeper"))).hasStatus(403);
    }

    private MvcTestResult put(MockHttpSession session, String uri, String json) {
        return mvc.put().uri(uri).session(session).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private void setQuantity(int quantity) {
        jdbc.sql("UPDATE dd_product_option_value SET quantity = :quantity, subtract = 1 WHERE product_option_value_id = 602")
                .param("quantity", quantity).update();
    }

    /** Stock and quota of size M. */
    private List<Integer> size() {
        return jdbc.sql("SELECT quantity, pre_order_quantity FROM dd_product_option_value WHERE product_option_value_id = 602")
                .query((rs, n) -> List.of(rs.getInt(1), rs.getInt(2))).single();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
