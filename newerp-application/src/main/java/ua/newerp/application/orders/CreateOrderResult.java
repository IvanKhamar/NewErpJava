package ua.newerp.application.orders;

import java.util.List;

/** warnings: what other modules reported after the status was set (for example, not enough sewn pieces). */
public record CreateOrderResult(int orderId, List<String> warnings, CustomerRiskDto risk) {
}
