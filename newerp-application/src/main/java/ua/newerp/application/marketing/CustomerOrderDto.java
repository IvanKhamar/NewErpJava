package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerOrderDto(int orderId, LocalDateTime dateAdded, BigDecimal total, String paymentMethod, String status, String products) {
}
