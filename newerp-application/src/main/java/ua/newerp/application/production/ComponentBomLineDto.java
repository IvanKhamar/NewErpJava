package ua.newerp.application.production;

import java.math.BigDecimal;

public record ComponentBomLineDto(Integer sizeId, int itemId, String itemName, String unit, BigDecimal qtyPerUnit) {
}
