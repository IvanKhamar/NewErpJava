package ua.newerp.application.stock;

import java.math.BigDecimal;

public record StockItemDto(int id, String name, String unit, Integer categoryId, String categoryName, Integer supplierId,
        String supplierName, BigDecimal quantity, BigDecimal minQuantity, boolean isBelowMinimum, String usedFor,
        BigDecimal price) {
}
