package ua.newerp.application.stock;

import java.math.BigDecimal;

public record ReceiptLineRequest(int lineId, BigDecimal quantity) {

    public ReceiptLineRequest {
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
    }
}
