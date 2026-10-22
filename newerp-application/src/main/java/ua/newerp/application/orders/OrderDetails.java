package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetails(
        int id,
        LocalDateTime dateAdded,
        String customer,
        String telephone,
        String email,
        String shippingMethod,
        String shippingCity,
        String shippingAddress,
        String paymentMethod,
        String comment,
        BigDecimal total,
        String currencyCode,
        int statusId,
        String statusName,
        int sourceId,
        String sourceName,
        Integer managerId,
        String managerName,
        List<OrderProductLine> products,
        List<OrderStatusHistoryEntry> statusHistory) {
}
