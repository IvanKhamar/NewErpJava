package ua.newerp.application.customers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** @param byPhone the order is not the customer's own but has the same phone (a guest order) */
public record CustomerOrderItem(int id, LocalDateTime dateAdded, int statusId, String statusName, BigDecimal total, String currencyCode,
        boolean byPhone) {
}
