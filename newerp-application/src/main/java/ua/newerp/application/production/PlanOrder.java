package ua.newerp.application.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** An active order for the sewing plan: its lines and how many sewn pieces it holds by size id. */
public record PlanOrder(int orderId, LocalDateTime dateAdded, List<OrderSizeLine> lines, Map<Integer, BigDecimal> held) {
}
