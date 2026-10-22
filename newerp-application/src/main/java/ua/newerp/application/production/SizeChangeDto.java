package ua.newerp.application.production;

import java.math.BigDecimal;

public record SizeChangeDto(int sizeId, String sizeName, BigDecimal before, BigDecimal after) {
}
