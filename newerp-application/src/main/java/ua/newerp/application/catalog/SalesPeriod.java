package ua.newerp.application.catalog;

import java.time.LocalDate;

/** The sales period: the last days days, or from to to inclusive. */
public record SalesPeriod(Integer days, LocalDate from, LocalDate to) {
}
