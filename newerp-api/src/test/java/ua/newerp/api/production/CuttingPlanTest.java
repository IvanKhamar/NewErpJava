package ua.newerp.api.production;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/stock.sql", "/sql/production.sql"})
// MERGE: a test's own @Sql runs after the class one instead of replacing it
@SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
class CuttingPlanTest extends IntegrationTest {

    @Test
    void cutsUpToTheMinimumBySizeSharesAndCountsTheFabric() {
        // "Сукня Лілія": minimum 10 = S 4 (40 %) + M 6 (60 %). S has 2 cut + 3 sewn, M has 1 sewn: cut 5 M.
        // Fabric for M: "Перед" 2 (its M row beats the general 1.5) + "Спинка" 1 = 3 × 5 = 15 of 50 m.
        // The ready-made T-shirt and the inactive model are not cut.
        assertThat(mvc.get().uri("/production/cut-plan").session(login("keeper"))).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"models":[{"productionId":1,"name":"Сукня Лілія","minTotal":10,"toCut":5,
                            "sizes":[{"name":"S","target":4,"ordered":0,"toCut":0},{"name":"M","target":6,"toCut":5}]}],
                 "items":[{"itemId":2,"name":"Бавовна","unit":"м","need":15.000,"stock":50.000,"shortage":0,"models":["Сукня Лілія"]}]}
                """);
        assertThat(mvc.get().uri("/production/cut-plan").session(login("keeper"))).bodyJson()
                .extractingPath("$.models").asArray().hasSize(1);
    }

    @Test
    @Sql(statements = "UPDATE dd_stock_production SET min_total = 60 WHERE production_id = 1")
    void showsTheShortageOfConsumables() {
        // S: 24 − 5 = 19 × 2.5 m = 47.5; M: 36 − 1 = 35 × 3 m = 105; 152.5 m needed, 50 on stock
        assertThat(mvc.get().uri("/production/cut-plan").session(login("keeper"))).bodyJson().isLenientlyEqualTo("""
                {"models":[{"toCut":54,"sizes":[{"target":24,"toCut":19},{"target":36,"toCut":35}]}],
                 "items":[{"itemId":2,"need":152.5000,"shortage":102.5000}]}
                """);
    }

    @Test
    @Sql(statements = "UPDATE dd_stock_production_sizes SET cut_stock = 10 WHERE size_id = 2")
    void enoughStockMeansAnEmptyPlanUnlessAllModelsAreAsked() {
        assertThat(mvc.get().uri("/production/cut-plan").session(login("keeper"))).bodyJson()
                .isLenientlyEqualTo("{\"models\":[],\"items\":[]}");
        assertThat(mvc.get().uri("/production/cut-plan?onlyToCut=false").session(login("admin"))).bodyJson()
                .isLenientlyEqualTo("{\"models\":[{\"productionId\":1,\"toCut\":0}]}");
        assertThat(mvc.get().uri("/production/cut-plan").session(login("olena"))).hasStatus(403);
    }
}
