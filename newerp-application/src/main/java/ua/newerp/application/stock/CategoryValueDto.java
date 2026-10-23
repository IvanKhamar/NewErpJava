package ua.newerp.application.stock;

import java.math.BigDecimal;

/** Stock value of one category: quantity × price of its items; negative stock counts as none. categoryId null: no category. */
public record CategoryValueDto(Integer categoryId, String name, int items, BigDecimal value) {
}
