package ua.newerp.api.production;

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

@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql"})
class ProductionControllerTest extends IntegrationTest {

    MockHttpSession keeper;

    @BeforeEach
    void logIn() {
        keeper = login("keeper");
    }

    @Test
    void onlyAdminAndStorekeeper() {
        assertThat(mvc.get().uri("/production/models").session(login("olena"))).hasStatus(403);
        assertThat(mvc.get().uri("/production/models").session(login("admin"))).hasStatusOk();
    }

    @Test
    void listsModels() {
        assertThat(mvc.get().uri("/production/models").session(keeper)).bodyJson().isLenientlyEqualTo("""
                [{"id":1,"name":"Сукня Лілія","type":"clothing","minTotal":10,"totalCut":2.000,"totalSewn":4.000,"deficit":6.000},
                 {"id":2,"name":"Футболка базова","type":"ready_made","minTotal":5,"totalCut":0.000,"totalSewn":8.000,"deficit":0}]
                """);
        assertThat(mvc.get().uri("/production/models?belowMinimumOnly=true").session(keeper)).bodyJson()
                .extractingPath("$[*].id").asArray().containsExactly(1);
        assertThat(mvc.get().uri("/production/models?type=ready_made").session(keeper)).bodyJson()
                .extractingPath("$[*].id").asArray().containsExactly(2);
    }

    @Test
    void showsModel() {
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"id":1,"name":"Сукня Лілія","minTotal":10,
                 "sizes":[{"id":1,"name":"S","percent":40.00,"cutStock":2.000,"sewnStock":3.000,"optionValueId":101},
                          {"id":2,"name":"M","optionValueId":null}],
                 "productIds":[50],
                 "categories":[{"categoryId":20,"nameFilter":"Лілія","matchExact":false}],
                 "components":[{"id":1,"name":"Перед","stock":{"1":2.000},
                                "bom":[{"sizeId":null,"itemId":2,"itemName":"Бавовна","unit":"м","qtyPerUnit":1.5000},
                                       {"sizeId":2,"itemId":2}]},
                               {"id":2,"name":"Спинка"}],
                 "sewnBom":[{"componentId":1,"componentName":"Перед","itemId":null},
                            {"componentId":2,"componentName":"Спинка"},
                            {"componentId":null,"itemId":4,"itemName":"Нитки","unit":"шт"}]}
                """);
        assertThat(mvc.get().uri("/production/models/3").session(keeper)).hasStatus(404);
        assertThat(mvc.get().uri("/production/models/99").session(keeper)).hasStatus(404);
    }

    @Test
    void createsAndUpdatesModelKeepingSizeStock() throws Exception {
        MvcTestResult created = post("/production/models", """
                {"name":"Блуза Віра","sizes":[{"name":"S","percent":50},{"name":"M","percent":50}]}
                """);
        assertThat(created).hasStatus(201);
        int id = idOf(created);
        assertThat(mvc.get().uri("/production/models/" + id).session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"name":"Блуза Віра","type":"clothing","minTotal":50,"sizes":[{"name":"S"},{"name":"M"}]}
                """);

        // Model 1: S keeps its id and stock, M (stock 1) cannot be removed
        assertThat(put("/production/models/1", """
                {"name":"Сукня Лілія","minTotal":12,"sizes":[{"name":"s","percent":30},{"name":"XL","percent":10}]}
                """)).hasStatus(400).bodyJson().extractingPath("$.detail")
                .isEqualTo("Розмір \"M\" не можна прибрати: на ньому є залишок.");
        assertThat(put("/production/models/1", """
                {"name":"Сукня Лілія","minTotal":12,"sizes":[{"name":"S","percent":30},{"name":"M","percent":60},{"name":"XL","percent":10}]}
                """)).hasStatus(204);
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"minTotal":12,"sizes":[{"id":1,"name":"S","percent":30.00,"sewnStock":3.000},{"id":2,"name":"M"},{"name":"XL"}]}
                """);

        assertThat(post("/production/models", "{\"name\":\"X\",\"type\":\"other\"}")).hasStatus(400);
    }

    @Test
    void cutWritesOffConsumablesByBom() {
        // Перед: 1.5 m per S, 2 m per M (size override); Спинка: 1 m. Бавовна has 50 m.
        assertThat(post("/production/models/1/cut", """
                {"quantities":[{"sizeId":1,"quantity":2},{"sizeId":2,"quantity":20}],"comment":"партія 7"}
                """)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"sizes":[{"sizeId":1,"sizeName":"S","before":2.000,"after":4.000},
                          {"sizeId":2,"sizeName":"M","before":0.000,"after":20.000}],
                 "warnings":["Бракує 15 м: Бавовна. Списано 5 з 20."]}
                """);
        // S: 2×1.5 + 2×1 = 5 m, 45 left; M: Перед takes 40, Спинка wants 20 and gets 5, but the components are still counted
        assertThat(mvc.get().uri("/stock/items/2").session(keeper)).bodyJson().isLenientlyEqualTo("{\"quantity\":0}");
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"components":[{"id":1,"stock":{"1":4.000,"2":20}},{"id":2,"stock":{"1":3.000,"2":20}}]}
                """);
        assertThat(mvc.get().uri("/production/log?productionId=1&stage=cut").session(keeper)).bodyJson().isLenientlyEqualTo("""
                [{"sizeName":"M","stage":"cut","direction":"in","quantity":20.000,"comment":"партія 7","username":"keeper",
                  "productionName":"Сукня Лілія"},
                 {"sizeName":"S","quantity":2.000}]
                """);
    }

    @Test
    void sewnTakesComponentsAndConsumables() {
        assertThat(post("/stock/items/4/adjustments", "{\"delta\":1,\"reason\":\"прихід\"}")).hasStatusOk();
        assertThat(post("/production/models/1/sewn", "{\"quantities\":[{\"sizeId\":1,\"quantity\":2}]}"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"sizes":[{"sizeId":1,"before":3.000,"after":5.000}],
                 "warnings":["Бракує розкроєних деталей «Спинка» розміру S: 1 шт. Взято 1 з 2.",
                             "Бракує 1 шт: Нитки. Списано 1 з 2."]}
                """);
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"components":[{"id":1,"stock":{"1":0.000}},{"id":2,"stock":{"1":0.000}}]}
                """);
    }

    @Test
    void stocktakingSetsCountedStockOfSizesAndComponents() {
        assertThat(post("/production/models/1/stocktaking", """
                {"sizes":[{"sizeId":1,"cut":2,"sewn":5},{"sizeId":2,"cut":1}],
                 "components":[{"componentId":1,"sizeId":1,"actual":0},{"componentId":2,"sizeId":2,"actual":3}],
                 "comment":"жовтень"}
                """)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"cut":[{"sizeId":2,"sizeName":"M","before":0.000,"after":1.000}],
                 "sewn":[{"sizeId":1,"sizeName":"S","before":3.000,"after":5.000}],
                 "components":2}
                """);
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"sizes":[{"cutStock":2.000,"sewnStock":5.000},{"cutStock":1.000,"sewnStock":1.000}],
                 "components":[{"id":1,"stock":{"1":0.000}},{"id":2,"stock":{"1":1.000,"2":3.000}}]}
                """);
        assertThat(mvc.get().uri("/production/log?productionId=1").session(keeper)).bodyJson()
                .extractingPath("$[0].comment").isEqualTo("Інвентаризація: жовтень");

        // A wrong row and nothing is written: the sewn stock of S stays 5
        assertThat(post("/production/models/1/stocktaking", "{\"sizes\":[{\"sizeId\":1,\"sewn\":0},{\"sizeId\":3,\"sewn\":1}]}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Розмір 3 не належить моделі \"Сукня Лілія\".");
        assertThat(post("/production/models/1/stocktaking", "{\"components\":[{\"componentId\":1,\"sizeId\":1,\"actual\":-1}]}"))
                .hasStatus(400);
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson()
                .extractingPath("$.sizes[0].sewnStock").isEqualTo(5.0);
        assertThat(post("/production/models/1/stocktaking", "{}")).hasStatus(400);
    }

    @Test
    void sendToSewingAndShipCannotGoBelowZero() {
        assertThat(post("/production/models/1/to-sewing", "{\"quantities\":[{\"sizeId\":1,\"quantity\":3}]}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Розмір S: розкроєно лише 2, а вказано 3.");
        assertThat(post("/production/models/1/to-sewing", "{\"quantities\":[{\"sizeId\":1,\"quantity\":2}]}")).hasStatusOk();
        assertThat(post("/production/models/2/ship", "{\"quantities\":[{\"sizeId\":3,\"quantity\":8}]}"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"sizes":[{"sizeId":3,"sizeName":"L","before":8.000,"after":0.000}],"warnings":[]}
                """);
        assertThat(post("/production/models/2/ship", "{\"quantities\":[{\"sizeId\":1,\"quantity\":1}]}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Розмір 1 не належить моделі \"Футболка базова\".");
        assertThat(post("/production/models/2/ship", "{\"quantities\":[]}")).hasStatus(400);
        assertThat(post("/production/models/3/ship", "{\"quantities\":[{\"sizeId\":1,\"quantity\":1}]}")).hasStatus(404);
    }

    @Test
    void componentsAndBoms() throws Exception {
        MvcTestResult created = post("/production/models/1/components", "{\"name\":\"Рукав\",\"sortOrder\":3}");
        assertThat(created).hasStatus(201);
        int id = idOf(created);

        assertThat(put("/production/components/" + id + "/bom", "{\"rows\":[{\"itemId\":5,\"qtyPerUnit\":1}]}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Розхідників 5 немає на складі.");
        assertThat(put("/production/components/" + id + "/bom", "{\"rows\":[{\"itemId\":2,\"qtyPerUnit\":0.5},{\"sizeId\":2,\"itemId\":2,\"qtyPerUnit\":0.7}]}"))
                .hasStatus(204);
        assertThat(put("/production/models/1/sewn-bom",
                "{\"rows\":[{\"componentId\":" + id + ",\"qtyPerUnit\":2},{\"itemId\":3,\"qtyPerUnit\":4}]}")).hasStatus(204);
        assertThat(put("/production/components/" + id, "{\"name\":\"Рукав довгий\",\"sortOrder\":3}")).hasStatus(204);
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"components":[{"id":1},{"id":2},{"name":"Рукав довгий","stock":{},
                               "bom":[{"sizeId":null,"qtyPerUnit":0.5000},{"sizeId":2,"qtyPerUnit":0.7000}]}],
                 "sewnBom":[{"componentName":"Рукав довгий","qtyPerUnit":2.0000},{"itemId":3,"itemName":"Ґудзики 15 мм"}]}
                """);

        // A component with cut stock stays; without stock it goes away with its sewing BOM rows
        assertThat(mvc.delete().uri("/production/components/1").session(keeper))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Деталь \"Перед\" має розкроєний запас, її не можна видалити.");
        assertThat(mvc.delete().uri("/production/components/" + id).session(keeper)).hasStatus(204);
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson()
                .extractingPath("$.sewnBom[*].itemId").asArray().containsExactly(3);
        assertThat(mvc.delete().uri("/production/components/" + id).session(keeper)).hasStatus(404);
    }

    @Test
    void linksAndDeactivate() {
        assertThat(put("/production/models/1/links", """
                {"productIds":[60,50],"categories":[{"categoryId":21,"nameFilter":"сукня","matchExact":true}],
                 "sizeOptions":[{"sizeId":2,"optionValueId":102}]}
                """)).hasStatus(204);
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).bodyJson().isLenientlyEqualTo("""
                {"productIds":[50,60],"categories":[{"categoryId":21,"nameFilter":"сукня","matchExact":true}],
                 "sizes":[{"id":1,"optionValueId":101},{"id":2,"optionValueId":102}]}
                """);

        assertThat(mvc.delete().uri("/production/models/1").session(keeper)).hasStatus(204);
        assertThat(mvc.get().uri("/production/models/1").session(keeper)).hasStatus(404);
        assertThat(mvc.get().uri("/production/models").session(keeper)).bodyJson()
                .extractingPath("$[*].id").asArray().containsExactly(2);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(keeper).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri).session(keeper).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static int idOf(MvcTestResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }
}
