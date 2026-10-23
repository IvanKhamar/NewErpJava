package ua.newerp.application.stock;

import java.math.BigDecimal;

/** booked: the quantity in the books now; difference = actual − booked (minus = shortage). */
public record StocktakingLineDto(int itemId, String name, String unit, BigDecimal booked, BigDecimal actual, BigDecimal difference) {
}
