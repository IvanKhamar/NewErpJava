package ua.newerp.application.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Orders and returns by status for a period, like the old site's "Статистика". Every placed order counts here, the cancelled ones
 * too: each status is a row of its own.
 */
public record StatusReportDto(LocalDate from, LocalDate to, List<OrderRow> orders, int orderCount, BigDecimal total, List<ReturnRow> returns,
        int returnCount) {

    public record OrderRow(int statusId, String name, int orders, BigDecimal total) {
    }

    /** quantity is the product units returned. */
    public record ReturnRow(int statusId, String name, int returns, int quantity) {
    }
}
