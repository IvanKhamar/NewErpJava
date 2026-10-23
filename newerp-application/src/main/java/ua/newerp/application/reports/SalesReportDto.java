package ua.newerp.application.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesReportDto(LocalDate from, LocalDate to, List<SalesReportRow> rows, int orders, int products, BigDecimal total) {
}
