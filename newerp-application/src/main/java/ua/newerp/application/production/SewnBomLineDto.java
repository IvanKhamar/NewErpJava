package ua.newerp.application.production;

import java.math.BigDecimal;

public record SewnBomLineDto(Integer sizeId, Integer componentId, String componentName, Integer itemId, String itemName,
        String unit, BigDecimal qtyPerUnit) {
}
