package ua.newerp.domain.customers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A customer's order in short: for the totals in the card. */
public record CustomerOrder(int orderId, LocalDateTime dateAdded, int statusId, BigDecimal total) {
}
