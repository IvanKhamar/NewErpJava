package ua.newerp.application.stock;

import java.math.BigDecimal;

/** changed: items whose quantity changed; value: the sum of differences × price (minus = shortage). */
public record StocktakingResult(int counted, int changed, BigDecimal value) {
}
