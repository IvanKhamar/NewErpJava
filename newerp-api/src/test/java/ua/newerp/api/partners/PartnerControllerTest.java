package ua.newerp.api.partners;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/catalog.sql", "/sql/partners.sql"})
class PartnerControllerTest extends IntegrationTest {

    private static final String REQUEST = """
            {"code":"eva","name":"EVA","feedEnabled":%s,"defaultQuantity":3,
             "categoryMap":[{"categoryId":30,"externalId":"77","percentSupply":%s,"percentSale":20}]}
            """;

    @Test
    void feedListsCategoryProductsWithMarkup() {
        MockHttpSession olena = login("olena");
        MvcTestResult created = send(olena, mvc.post().uri("/partners"), REQUEST.formatted(true, 10));
        assertThat(created).hasStatus(201);
        int id = JsonPath.read(body(created), "$.id");

        MvcTestResult partner = mvc.get().uri("/partners/" + id).session(olena).exchange();
        assertThat(partner).bodyJson().isLenientlyEqualTo("""
                {"code":"eva","vendorName":"Vidro","isActive":true,"categoryMap":[{"categoryId":30,"categoryName":"Сорочки","sizeName":null}]}
                """);
        int rowId = JsonPath.read(body(partner), "$.categoryMap[0].id");
        assertThat(mvc.get().uri("/partners/categories").session(olena)).bodyJson()
                .isLenientlyEqualTo("[{\"id\":30,\"name\":\"Сорочки\",\"sizes\":[\"M\"]}]");

        // The feed opens without a login, as the partner's site fetches it.
        MvcTestResult feed = mvc.get().uri("/feeds/eva.xml").exchange();
        assertThat(feed).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_XML);
        assertThat(feed).bodyText()
                .contains("id=\"60_102\"", "available=\"true\"")
                .contains("<price>990</price>", "<price_old>1080</price_old>", "<stock_quantity>3</stock_quantity>")
                .contains("<categoryId>" + rowId + "</categoryId>")
                .contains("<picture>image/catalog/main.jpg</picture>", "<picture>image/catalog/%D0%B7%D0%B0%D0%B4.jpg</picture>")
                .contains("<name_ua>Сорочка лляна оверсайз - M</name_ua>");

        // A changed mapping keeps its feed category id; a switched off feed is 404.
        assertThat(send(olena, mvc.put().uri("/partners/" + id), REQUEST.formatted(false, 15))).hasStatus(204);
        assertThat(mvc.get().uri("/partners/" + id).session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"categoryMap\":[{\"id\":%d,\"percentSupply\":15.00}]}".formatted(rowId));
        assertThat(mvc.get().uri("/feeds/eva.xml")).hasStatus(404);

        assertThat(send(olena, mvc.post().uri("/partners"), REQUEST.formatted(true, 10))).hasStatus(400); // the code is taken
        assertThat(mvc.get().uri("/partners").session(login("keeper"))).hasStatus(403);
        assertThat(mvc.get().uri("/partners/products?search=Сорочка").session(olena)).bodyJson()
                .isLenientlyEqualTo("[{\"id\":60,\"isEnabled\":true,\"sizes\":[{\"name\":\"M\",\"price\":900.0000}]},{\"id\":62,\"isEnabled\":false}]");

        assertThat(mvc.delete().uri("/partners/" + id).session(olena)).hasStatus(204);
        assertThat(mvc.get().uri("/partners").session(olena)).bodyJson().isStrictlyEqualTo("[]");
    }

    private MvcTestResult send(MockHttpSession session, MockMvcRequestBuilder request, String json) {
        return request.session(session).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
