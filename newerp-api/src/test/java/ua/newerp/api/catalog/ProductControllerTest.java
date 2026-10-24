package ua.newerp.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;

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
    void storekeeperHasNoAccess() {
        assertThat(mvc.get().uri("/catalog/products").session(login("keeper"))).hasStatus(403);
    }
}
