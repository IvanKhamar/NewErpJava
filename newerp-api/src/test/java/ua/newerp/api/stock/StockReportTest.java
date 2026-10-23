package ua.newerp.api.stock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql", "/sql/stock-report.sql"})
class StockReportTest extends IntegrationTest {

    @Test
    void valuesStockByCategoryAndMeasuresUseWithoutStocktaking() {
        // Тканини: 3 × 200 + 50 × 120; Фурнітура: 20 × 1.5; Нитки have no category and no stock.
        // Бавовна used 15 + 5 in 30 days (the 3 counted short is a correction, the 100 of 60 days ago is out): 50 m last 75 days.
        // Льон: 6 used, 3 left = 15 days; for the next 30 days order 6 − 3 = 3. The archived item is left out.
        assertThat(mvc.get().uri("/stock/reports").session(login("keeper"))).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"days":30,"coverDays":30,
                 "categories":[{"categoryId":1,"name":"Тканини","items":2,"value":6600},
                               {"categoryId":2,"name":"Фурнітура","items":1,"value":30},
                               {"categoryId":null,"name":"Без категорії","items":1,"value":0}],
                 "totalValue":6630,
                 "consumption":[{"itemId":1,"used":6.000,"received":0.000,"corrected":0.000,"perDay":0.200,"daysLeft":15,"suggested":3,"sum":600},
                                {"itemId":2,"used":20.000,"received":20.000,"corrected":-3.000,"perDay":0.667,"daysLeft":75,"suggested":0}],
                 "suggestedTotal":600}
                """);
    }

    @Test
    void suggestsEnoughForTheLongerCoverAndTakesWhatIsOnOrder() {
        // 90 days of Бавовна at 20 per 30 days = 60, 50 on stock: 10 more. Льон: 18 − 3 − 5 on order = 10.
        assertThat(mvc.post().uri("/stock/purchase-orders").session(login("keeper")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":1,\"lines\":[{\"itemId\":1,\"quantity\":5,\"price\":200}]}")).hasStatus(201);
        assertThat(mvc.get().uri("/stock/reports?coverDays=90").session(login("keeper"))).bodyJson().isLenientlyEqualTo("""
                {"consumption":[{"itemId":1,"onOrder":5,"suggested":10,"sum":2000},{"itemId":2,"suggested":10,"sum":1200}],
                 "suggestedTotal":3200}
                """);
    }

    @Test
    void countsProductionOutputBySize() {
        // S: 5 cut, 3 sewn (the cancelled order's piece coming back is not new output), 2 sold − 1 returned, 1 for a photo shoot,
        // +2 sewn and −1 cut found at stocktaking. M was cut 60 days ago. The T-shirt was received and sold through an order.
        assertThat(mvc.get().uri("/stock/reports").session(login("admin"))).bodyJson().isLenientlyEqualTo("""
                {"output":[{"productionId":1,"name":"Сукня Лілія","cut":5.000,"sewn":3.000,"sold":1.000,"shipped":1.000,"corrected":1.000,
                            "sizes":[{"sizeId":1,"name":"S","cut":5.000,"sewn":3.000,"sold":1.000,"shipped":1.000,"corrected":1.000}]},
                           {"productionId":2,"name":"Футболка базова","cut":0.000,"sewn":10.000,"sold":4.000,"shipped":0.000,"corrected":0.000}]}
                """);
        assertThat(mvc.get().uri("/stock/reports").session(login("admin"))).bodyJson().extractingPath("$.output[0].sizes")
                .asArray().hasSize(1);
    }

    @Test
    void periodIsCheckedAndOnlyStockRolesSeeIt() {
        assertThat(mvc.get().uri("/stock/reports?days=0").session(login("keeper"))).hasStatus(400);
        assertThat(mvc.get().uri("/stock/reports?days=400").session(login("keeper"))).hasStatus(400);
        assertThat(mvc.get().uri("/stock/reports?days=7").session(login("keeper"))).bodyJson().extractingPath("$.days").isEqualTo(7);
        assertThat(mvc.get().uri("/stock/reports").session(login("olena"))).hasStatus(403);
    }
}
