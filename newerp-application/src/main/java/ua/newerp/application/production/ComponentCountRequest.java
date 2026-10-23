package ua.newerp.application.production;

import java.math.BigDecimal;

public record ComponentCountRequest(int componentId, int sizeId, BigDecimal actual) {
}
