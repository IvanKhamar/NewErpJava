package ua.newerp.application.stock;

import java.math.BigDecimal;

public record SaveStockItemRequest(Integer categoryId, Integer supplierId, String name, String unit, BigDecimal minQuantity,
        String usedFor, BigDecimal price) {

    /** Compact constructor: runs before the fields are set; here it fills defaults like "= 0" in C#. */
    public SaveStockItemRequest {
        minQuantity = minQuantity == null ? BigDecimal.ZERO : minQuantity;
        price = price == null ? BigDecimal.ZERO : price;
    }
}
