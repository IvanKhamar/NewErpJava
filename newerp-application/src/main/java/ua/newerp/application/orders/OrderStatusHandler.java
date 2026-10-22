package ua.newerp.application.orders;

import java.util.List;

import ua.newerp.domain.orders.OrderStatusChange;

/**
 * What has to happen in other modules when an order changes status (for example, writing off sewn pieces).
 * OrderService calls every handler bean before the new status is saved.
 */
public interface OrderStatusHandler {

    /** @return warnings for the user (empty when all is fine) */
    List<String> onStatusChanging(OrderStatusChange change);
}
