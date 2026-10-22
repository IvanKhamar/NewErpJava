package ua.newerp.application.production;

import java.math.BigDecimal;

/** Either componentId (a cut component) or itemId (a consumable directly). */
public record SewnBomRowRequest(Integer sizeId, Integer componentId, Integer itemId, BigDecimal qtyPerUnit) {

    public SewnBomRowRequest {
        qtyPerUnit = qtyPerUnit == null ? BigDecimal.ZERO : qtyPerUnit;
    }
}
