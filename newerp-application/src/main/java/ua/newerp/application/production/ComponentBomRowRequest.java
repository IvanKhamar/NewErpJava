package ua.newerp.application.production;

import java.math.BigDecimal;

/** sizeId = null: for all sizes; a row with sizeId overrides it for that size. */
public record ComponentBomRowRequest(Integer sizeId, int itemId, BigDecimal qtyPerUnit) {

    public ComponentBomRowRequest {
        qtyPerUnit = qtyPerUnit == null ? BigDecimal.ZERO : qtyPerUnit;
    }
}
