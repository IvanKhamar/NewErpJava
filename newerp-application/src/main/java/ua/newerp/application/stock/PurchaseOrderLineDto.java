package ua.newerp.application.stock;

import java.math.BigDecimal;

public record PurchaseOrderLineDto(int id, int itemId, String itemName, String unit, BigDecimal quantity, BigDecimal price,
        BigDecimal sum, BigDecimal received, BigDecimal remaining) {
}
