package ua.newerp.application.catalog;

import java.math.BigDecimal;

/** A cross-sell panel line: type product or category, its name, and for a category how many enabled products it brings. */
public record CrossSellItemDto(int id, String type, int targetId, String name, BigDecimal discount, int products) {
}
