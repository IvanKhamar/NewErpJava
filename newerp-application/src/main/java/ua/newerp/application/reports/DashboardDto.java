package ua.newerp.application.reports;

import java.util.List;

public record DashboardDto(DashboardTile ordersToday, DashboardTile salesToday, DashboardTile ordersMonth, DashboardTile salesMonth,
        DashboardTile averageCheckMonth, DashboardTile newCustomersMonth, List<SalesReportRow> last30Days,
        List<ProductSalesRow> topProducts30Days) {
}
