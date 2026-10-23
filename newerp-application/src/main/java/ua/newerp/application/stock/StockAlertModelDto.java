package ua.newerp.application.stock;

import java.math.BigDecimal;

/** A garment model whose sewn stock is below its minimum; deficit = minTotal − sewn. */
public record StockAlertModelDto(int productionId, String name, int minTotal, BigDecimal sewn, BigDecimal deficit) {
}
