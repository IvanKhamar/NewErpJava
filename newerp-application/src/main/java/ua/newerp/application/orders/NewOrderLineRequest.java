package ua.newerp.application.orders;

import java.math.BigDecimal;

public record NewOrderLineRequest(int productId, Integer productOptionValueId, int quantity, BigDecimal price) {
}
