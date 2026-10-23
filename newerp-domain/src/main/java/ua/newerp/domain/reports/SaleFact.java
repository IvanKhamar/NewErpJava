package ua.newerp.domain.reports;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One order for the report: date, total and how many product units are in it. */
public record SaleFact(LocalDateTime dateAdded, BigDecimal total, int products) {
}
