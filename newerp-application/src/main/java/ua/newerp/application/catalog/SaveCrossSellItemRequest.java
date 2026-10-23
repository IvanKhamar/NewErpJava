package ua.newerp.application.catalog;

import java.math.BigDecimal;

/** type: product or category; discount in percent, 0 or null for none. */
public record SaveCrossSellItemRequest(String type, int targetId, BigDecimal discount) {
}
