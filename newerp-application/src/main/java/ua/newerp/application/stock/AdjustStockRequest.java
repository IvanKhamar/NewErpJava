package ua.newerp.application.stock;

import java.math.BigDecimal;

/** delta: plus = receipt, minus = use. */
public record AdjustStockRequest(BigDecimal delta, String reason) {

    public AdjustStockRequest {
        delta = delta == null ? BigDecimal.ZERO : delta;
    }
}
