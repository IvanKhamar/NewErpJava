package ua.newerp.api.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

/** Editing and removing categories and suppliers, archiving items. */
@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql"})
class StockReferencesTest extends IntegrationTest {

    MockHttpSession keeper;

    @BeforeEach
    void logIn() {
        keeper = login("keeper");
    }

    @Test
    void categoriesAreRenamedAndOnlyEmptyOnesDeleted() {
        assertThat(send("PUT", "/stock/categories/2", "{\"name\":\"Фурнітура й нитки\",\"sortOrder\":5}")).hasStatus(204);
        assertThat(mvc.get().uri("/stock/categories").session(keeper)).bodyJson()
                .isLenientlyEqualTo("[{\"id\":1,\"name\":\"Тканини\"},{\"id\":2,\"name\":\"Фурнітура й нитки\"}]");
        assertThat(send("PUT", "/stock/categories/2", "{\"name\":\"Тканини\"}")).hasStatus(400);

        assertThat(send("DELETE", "/stock/categories/1", null)).hasStatus(400).bodyJson().extractingPath("$.detail")
                .asString().contains("позицій — 3"); // two active and the archived one
        int empty = JsonPath.read(body(send("POST", "/stock/categories", "{\"name\":\"Пакування\"}")), "$.id");
        assertThat(send("DELETE", "/stock/categories/" + empty, null)).hasStatus(204);
        assertThat(send("DELETE", "/stock/categories/" + empty, null)).hasStatus(404);
    }

    @Test
    void suppliersAreEditedAndOnlyUnusedOnesDeleted() {
        assertThat(send("PUT", "/stock/suppliers/2",
                "{\"name\":\"Ґудзик і Ко\",\"phone\":\"+380501234567\",\"email\":\"\",\"categories\":\"фурнітура, нитки\",\"notes\":\"\"}"))
                .hasStatus(204);
        assertThat(mvc.get().uri("/stock/suppliers").session(keeper)).bodyJson()
                .extractingPath("$[?(@.id == 2)].phone").asArray().containsExactly("+380501234567");

        assertThat(send("DELETE", "/stock/suppliers/1", null)).hasStatus(400);
        int unused = JsonPath.read(body(send("POST", "/stock/suppliers", "{\"name\":\"Тимчасовий\"}")), "$.id");
        assertThat(send("DELETE", "/stock/suppliers/" + unused, null)).hasStatus(204);
    }

    @Test
    void anItemIsArchivedOnlyWhenNothingNeedsIt() {
        // Linen still has 3 m; cotton is in the cutting BOM of the dress; buttons are in an open purchase order
        assertThat(send("POST", "/stock/items/1/archive", null)).hasStatus(400).bodyJson().extractingPath("$.detail")
                .asString().contains("на складі ще 3 м");
        assertThat(send("POST", "/stock/items/2/archive", null)).hasStatus(400).bodyJson().extractingPath("$.detail")
                .asString().contains("Сукня Лілія");
        int order = JsonPath.read(body(send("POST", "/stock/purchase-orders",
                "{\"supplierId\":2,\"lines\":[{\"itemId\":3,\"quantity\":10,\"price\":1.5}]}")), "$.id");
        assertThat(send("POST", "/stock/items/3/archive", null)).hasStatus(400).bodyJson().extractingPath("$.detail")
                .asString().contains("#" + order);

        int tape = JsonPath.read(body(send("POST", "/stock/items", "{\"name\":\"Стрічка\",\"unit\":\"м\"}")), "$.id");
        assertThat(send("POST", "/stock/items/" + tape + "/archive", null)).hasStatus(204);
        assertThat(mvc.get().uri("/stock/items").session(keeper)).bodyJson()
                .extractingPath("$[?(@.name == 'Стрічка')]").asArray().isEmpty();
        assertThat(send("POST", "/stock/items/99/archive", null)).hasStatus(404);
    }

    private MvcTestResult send(String method, String uri, String json) {
        var request = switch (method) {
            case "PUT" -> mvc.put().uri(uri);
            case "DELETE" -> mvc.delete().uri(uri);
            default -> mvc.post().uri(uri);
        };
        request.session(keeper);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
