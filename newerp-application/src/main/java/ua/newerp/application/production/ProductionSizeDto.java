package ua.newerp.application.production;

import java.math.BigDecimal;

public record ProductionSizeDto(int id, String name, BigDecimal percent, BigDecimal cutStock, BigDecimal sewnStock,
        Integer optionValueId) {
}
