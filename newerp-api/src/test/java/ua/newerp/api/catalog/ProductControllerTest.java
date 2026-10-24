package ua.newerp.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/products.sql"})
class ProductControllerTest extends IntegrationTest {

    @Test
    void listFiltersAndPages() {
        MockHttpSession olena = login("olena");

        assertThat(mvc.get().uri("/catalog/products").session(olena)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"totalCount\":3,\"items\":[{\"id\":71,\"name\":\"Пояс шкіряний\",\"enabled\":false},"
                        + "{\"id\":70,\"name\":\"Сукня лляна\",\"price\":1500.0000,\"quantity\":6,\"enabled\":true},{\"id\":72}]}");
        assertThat(mvc.get().uri("/catalog/products?categoryId=30&enabled=true").session(olena)).bodyJson()
                .extractingPath("$.items[*].id").isEqualTo(List.of(70, 72));
        assertThat(mvc.get().uri("/catalog/products?labelId=1").session(olena)).bodyJson().extractingPath("$.items[*].id").isEqualTo(List.of(70));
        assertThat(mvc.get().uri("/catalog/products?enabled=false").session(olena)).bodyJson().extractingPath("$.items[*].id").isEqualTo(List.of(71));
        // % is a plain character in the search, not a LIKE wildcard; a number finds the product itself.
        assertThat(mvc.get().uri("/catalog/products?search=50%").session(olena)).bodyJson().extractingPath("$.items[*].id").isEqualTo(List.of(72));
        assertThat(mvc.get().uri("/catalog/products?search=71").session(olena)).bodyJson().extractingPath("$.items[*].id").isEqualTo(List.of(71));
        assertThat(mvc.get().uri("/catalog/products?page=2&pageSize=2").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"page\":2,\"totalPages\":2,\"items\":[{\"id\":72}]}");
    }

    @Test
    void cardWithSizesLabelsAndCategories() {
        MockHttpSession olena = login("olena");

        assertThat(mvc.get().uri("/catalog/products/70").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"id":70,"name":"Сукня лляна","model":"SL-01","dateModified":"2026-10-01T10:00:00",
                 "sizes":[{"optionValueId":102,"name":"M","quantity":4},{"optionValueId":103,"name":"L","quantity":2}],
                 "labels":[{"id":1,"name":"Хіт"},{"id":2,"name":"Новинка"}],
                 "categories":[{"id":30,"name":"Сукні"}]}""");
        assertThat(mvc.get().uri("/catalog/products/999").session(olena)).hasStatus(404);

        assertThat(mvc.get().uri("/catalog/products/labels").session(olena)).bodyJson().extractingPath("$[*].name")
                .isEqualTo(List.of("Хіт", "Новинка"));
        // Switched-off categories are not offered.
        assertThat(mvc.get().uri("/catalog/products/categories").session(olena)).bodyJson().extractingPath("$[*].name")
                .isEqualTo(List.of("Аксесуари", "Сукні"));
    }

    @Test
    void patchChangesPriceStatusLabelsAndCategoriesOnlyForTheVersionSeen() {
        MockHttpSession olena = login("olena");

        MvcTestResult changed = patch(olena, 70, """
                {"price":1399.50,"enabled":false,"labelIds":[2],"categoryIds":[30,31],"dateModified":"2026-10-01T10:00:00"}""");
        assertThat(changed).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"id":70,"price":1399.5000,"enabled":false,"labels":[{"id":2}],"categories":[{"id":31},{"id":30}]}""");
        assertThat(changed).bodyJson().extractingPath("$.dateModified").isNotEqualTo("2026-10-01T10:00:00");
        // The sizes and their stock are not touched.
        assertThat(changed).bodyJson().extractingPath("$.sizes.length()").isEqualTo(2);

        // The same old version again: somebody (here: we) changed the product since.
        assertThat(patch(olena, 70, "{\"price\":1000,\"dateModified\":\"2026-10-01T10:00:00\"}")).hasStatus(409);
        assertThat(mvc.get().uri("/catalog/products/70").session(olena)).bodyJson().extractingPath("$.price").isEqualTo(1399.5);

        // Null parts stay: only the labels of 72 change.
        assertThat(patch(olena, 72, "{\"labelIds\":[1],\"dateModified\":\"2026-01-01T00:00:00\"}")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"price\":900.0000,\"enabled\":true,\"labels\":[{\"id\":1}],\"categories\":[{\"id\":30}]}");
    }

    @Test
    void patchRefusesBadValuesAndUnknownProducts() {
        MockHttpSession olena = login("olena");
        String version = ",\"dateModified\":\"2026-01-01T00:00:00\"}";

        assertThat(patch(olena, 71, "{\"price\":-1" + version)).hasStatus(400);
        assertThat(patch(olena, 71, "{\"price\":10.005" + version)).hasStatus(400);
        assertThat(patch(olena, 71, "{\"labelIds\":[9]" + version)).hasStatus(400);
        assertThat(patch(olena, 71, "{\"categoryIds\":[]" + version)).hasStatus(400);
        // Category 32 is switched off: it cannot be newly chosen.
        assertThat(patch(olena, 71, "{\"categoryIds\":[32]" + version)).hasStatus(400);
        assertThat(patch(olena, 71, "{\"price\":100}")).hasStatus(400);
        assertThat(patch(olena, 999, "{\"price\":100" + version)).hasStatus(404);
        // Nothing of the refused changes reached the product.
        assertThat(mvc.get().uri("/catalog/products/71").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"price\":500.0000,\"dateModified\":\"2026-01-01T00:00:00\",\"categories\":[{\"id\":31}]}");
    }

    @Test
    void storekeeperHasNoAccess() {
        MockHttpSession keeper = login("keeper");
        assertThat(mvc.get().uri("/catalog/products").session(keeper)).hasStatus(403);
        assertThat(patch(keeper, 70, "{\"price\":1,\"dateModified\":\"2026-10-01T10:00:00\"}")).hasStatus(403);
    }

    private MvcTestResult patch(MockHttpSession session, int id, String json) {
        return mvc.patch().uri("/catalog/products/" + id).session(session).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
