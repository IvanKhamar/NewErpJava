package ua.newerp.domain.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A report row: the period [start; end] and what was sold in it. */
public record SalesPeriod(LocalDate start, LocalDate end, int orders, int products, BigDecimal total) {
}
