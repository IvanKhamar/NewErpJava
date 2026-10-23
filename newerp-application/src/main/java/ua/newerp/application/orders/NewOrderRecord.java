package ua.newerp.application.orders;

import java.time.LocalDateTime;
import java.util.List;

import ua.newerp.domain.orders.OrderDraft;

/** What to write to the database for a new order (all checks passed). */
public record NewOrderRecord(CreateOrderRequest request, OrderDraft draft, List<NewOrderLineRecord> lines, LocalDateTime createdAt) {
}
