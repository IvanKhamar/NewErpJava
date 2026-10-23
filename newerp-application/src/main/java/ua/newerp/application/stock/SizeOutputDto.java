package ua.newerp.application.stock;

import java.math.BigDecimal;

public record SizeOutputDto(int sizeId, String name, BigDecimal cut, BigDecimal sewn, BigDecimal sold, BigDecimal shipped,
        BigDecimal corrected) {
}
