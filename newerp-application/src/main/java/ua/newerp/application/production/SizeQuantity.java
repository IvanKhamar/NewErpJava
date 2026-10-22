package ua.newerp.application.production;

import java.math.BigDecimal;

public record SizeQuantity(int sizeId, BigDecimal quantity) {

    public SizeQuantity {
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
    }
}
