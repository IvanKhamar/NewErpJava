package ua.newerp.application.orders;

import java.math.BigDecimal;

public record OrderProductLine(int productId, String name, String model, int quantity, BigDecimal price, BigDecimal total) {
}
