package ua.newerp.application.stock;

import java.math.BigDecimal;

public record StocktakingCountRequest(int itemId, BigDecimal actual) {
}
