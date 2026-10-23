package ua.newerp.application.orders;

import java.math.BigDecimal;

public record ProductOptionValueDto(int productOptionId, int productOptionValueId, String optionName, String value, int quantity,
        BigDecimal price) {
}
