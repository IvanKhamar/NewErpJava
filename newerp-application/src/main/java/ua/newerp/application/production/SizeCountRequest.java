package ua.newerp.application.production;

import java.math.BigDecimal;

/** Counted stock of one size; null: that stock was not counted. */
public record SizeCountRequest(int sizeId, BigDecimal cut, BigDecimal sewn) {
}
