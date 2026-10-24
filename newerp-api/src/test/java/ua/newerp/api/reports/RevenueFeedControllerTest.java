package ua.newerp.api.reports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/reports.sql"})
@TestPropertySource(properties = "revenue-feed.token=dashboard-test-token")
class RevenueFeedControllerTest extends IntegrationTest {

    @Test
    void theDashboardGetsRevenueWithItsTokenAndNobodyElseDoes() {
        assertThat(mvc.get().uri("/feeds/revenue.json?days=7").header("X-Feed-Token", "dashboard-test-token")).hasStatusOk()
                .bodyJson().extractingPath("$.days.length()").isEqualTo(7);
        assertThat(mvc.get().uri("/feeds/revenue.json?token=dashboard-test-token")).hasStatusOk()
                .bodyJson().extractingPath("$.currency").isEqualTo("UAH");
        assertThat(mvc.get().uri("/feeds/revenue.json")).hasStatus(401);
        assertThat(mvc.get().uri("/feeds/revenue.json").header("X-Feed-Token", "guess")).hasStatus(401);
        assertThat(mvc.get().uri("/feeds/revenue.json?days=5000").header("X-Feed-Token", "dashboard-test-token")).hasStatus(400);
    }
}
