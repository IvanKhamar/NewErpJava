package ua.newerp.api.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/reports.sql"})
class ReportControllerTest extends IntegrationTest {

    @Test
    void salesByMonthWithoutCancelledAndUnfinishedProductsAndDashboard() {
        MockHttpSession olena = login("olena");

        MvcTestResult sales = mvc.get().uri("/reports/sales?from=2025-01-01&to=2026-10-31&group=Month").session(olena).exchange();
        assertThat(sales).hasStatusOk().bodyJson().extractingPath("$.rows[*].start").isEqualTo(List.of("2026-08-01", "2026-09-01", "2026-10-01"));
        assertThat(sales).bodyJson().extractingPath("$.rows[*].total").isEqualTo(List.of(1200.0, 900.0, 3500.0));
        assertThat(sales).bodyJson().extractingPath("$.orders").isEqualTo(4);
        assertThat(sales).bodyJson().extractingPath("$.rows[2].products").isEqualTo(3);

        assertThat(mvc.get().uri("/reports/sales?from=2025-01-01&to=2026-10-31&statusId=19").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"orders\":1,\"total\":700.0000}");

        assertThat(mvc.get().uri("/reports/products?from=2026-10-01&to=2026-10-31").session(olena)).bodyJson()
                .isLenientlyEqualTo("{\"rows\":[{\"productId\":50,\"quantity\":2,\"name\":\"Сукня лляна\"},{\"productId\":51,\"quantity\":1}]}");

        assertThat(mvc.get().uri("/reports/dashboard").session(olena)).hasStatusOk().bodyJson().extractingPath("$.last30Days.length()")
                .isEqualTo(30);

        assertThat(mvc.get().uri("/reports/sales?from=2026-10-05&to=2026-10-01").session(olena)).hasStatus(400);
        assertThat(mvc.get().uri("/reports/dashboard").session(login("keeper"))).hasStatus(403);
    }
}
