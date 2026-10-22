package ua.newerp.application.stock;

import java.math.BigDecimal;

public record PurchaseOrderLineRequest(int itemId, BigDecimal quantity, BigDecimal price) {

    public PurchaseOrderLineRequest {
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        price = price == null ? BigDecimal.ZERO : price;
    }
}
