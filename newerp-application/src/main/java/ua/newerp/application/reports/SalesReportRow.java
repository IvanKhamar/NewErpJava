package ua.newerp.application.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesReportRow(LocalDate start, LocalDate end, int orders, int products, BigDecimal total) {
}
