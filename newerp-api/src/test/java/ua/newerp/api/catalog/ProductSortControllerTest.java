package ua.newerp.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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
import ua.newerp.domain.catalog.SortGroupType;

@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/product-sort.sql"})
class ProductSortControllerTest extends IntegrationTest {

    private static final String PREVIEW = "/catalog/sort/preview?type=Category&key=30&from=2026-10-01&to=2026-10-10";
    private static final int CATEGORY = SortGroupType.CATEGORY.ordinal();

    @Autowired
    JdbcClient jdbc;

    @Test
    void categorySortsBySalesWithPinsAndRestoresSnapshot() {
        MockHttpSession olena = login("olena");

        assertThat(mvc.get().uri("/catalog/sort/groups").session(olena)).bodyJson().isLenientlyEqualTo("""
                [{"type":%d,"key":"30","name":"Сукні"},{"type":%d,"key":"Хіт","name":"Мітка: Хіт"},{"type":%d,"key":"panel"}]
                """.formatted(CATEGORY, SortGroupType.LABEL.ordinal(), SortGroupType.CROSS_SELL.ordinal()))
                .extractingPath("$[-1:].key").isEqualTo(List.of("panel"));

        // The wholesale order ("б2б" in the comment) and the one created by user 13 do not count.
        MvcTestResult preview = mvc.get().uri(PREVIEW).session(olena).exchange();
        assertThat(preview).bodyJson().extractingPath("$[*].productId").isEqualTo(List.of(202, 201, 203));
        assertThat(preview).bodyJson().extractingPath("$[*].sales").isEqualTo(List.of(3, 1, 0));

        assertThat(post(olena, "/catalog/sort/pins", "{\"type\":%d,\"key\":\"30\",\"position\":1,\"productId\":203}".formatted(CATEGORY)))
                .hasStatus(204);
        preview = mvc.get().uri(PREVIEW).session(olena).exchange();
        assertThat(preview).bodyJson().extractingPath("$[*].productId").isEqualTo(List.of(203, 202, 201));
        assertThat(preview).bodyJson().extractingPath("$[*].pinned").isEqualTo(List.of(true, false, false));

        MvcTestResult applied = post(olena, "/catalog/sort/apply",
                "{\"type\":%d,\"key\":\"30\",\"productIds\":[203,202,201]}".formatted(CATEGORY));
        assertThat(applied).hasStatusOk().bodyJson().extractingPath("$.updated").isEqualTo(3);
        assertThat(sortOrders()).containsExactly(3, 2, 1);

        int snapshotId = JsonPath.read(body(applied), "$.snapshotId");
        assertThat(mvc.post().uri("/catalog/sort/snapshots/" + snapshotId + "/restore").session(olena)).hasStatus(204);
        assertThat(sortOrders()).containsExactly(0, 0, 0);

        MvcTestResult pins = mvc.get().uri("/catalog/sort/pins").session(olena).exchange();
        assertThat(pins).bodyJson().isLenientlyEqualTo("[{\"productName\":\"Сукня 203\",\"position\":1}]");
        int pinId = JsonPath.read(body(pins), "$[0].id");
        assertThat(mvc.delete().uri("/catalog/sort/pins/" + pinId).session(olena)).hasStatus(204);

        assertThat(mvc.put().uri("/catalog/sort/crosssell-metric").session(olena).contentType(MediaType.APPLICATION_JSON)
                .content("{\"panelOnly\":true}")).hasStatusOk();
        assertThat(mvc.get().uri("/catalog/sort/crosssell-metric").session(olena)).bodyJson().isStrictlyEqualTo("{\"panelOnly\":true}");

        assertThat(post(olena, "/catalog/sort/recalculate-all", "{\"from\":\"2026-10-01\",\"to\":\"2026-10-10\"}")).hasStatusOk();
        // The label goes after the categories, so its order overrides the category's: 202 and 201 are first in the label.
        assertThat(sortOrders()).containsExactly(2, 1, 3);

        assertThat(mvc.get().uri("/catalog/sort/groups").session(login("keeper"))).hasStatus(403);
    }

    private List<Integer> sortOrders() {
        return jdbc.sql("SELECT sort_order FROM dd_product WHERE product_id > 200 ORDER BY product_id").query(Integer.class).list();
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
