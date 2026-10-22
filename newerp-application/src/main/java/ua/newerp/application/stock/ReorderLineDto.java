package ua.newerp.application.stock;

import java.math.BigDecimal;

/**
 * An item to reorder. onOrder: already ordered and not received yet (subtracted from the suggestion).
 * suggested is rounded up to a whole number, because suppliers sell whole metres and pieces.
 */
public record ReorderLineDto(int itemId, String name, String unit, BigDecimal quantity, BigDecimal minQuantity,
        BigDecimal onOrder, BigDecimal suggested, BigDecimal price, BigDecimal sum) {
}
