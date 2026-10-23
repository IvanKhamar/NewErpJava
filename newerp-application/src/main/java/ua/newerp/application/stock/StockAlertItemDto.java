package ua.newerp.application.stock;

import java.math.BigDecimal;

/** A consumable below its minimum. onOrder: ordered from the supplier and not received yet (0 when nothing). */
public record StockAlertItemDto(int itemId, String name, String unit, BigDecimal quantity, BigDecimal minQuantity, BigDecimal onOrder) {
}
