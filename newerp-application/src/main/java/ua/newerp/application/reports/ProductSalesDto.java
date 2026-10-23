package ua.newerp.application.reports;

import java.time.LocalDate;
import java.util.List;

public record ProductSalesDto(LocalDate from, LocalDate to, List<ProductSalesRow> rows) {
}
