package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.util.List;

/** What was cut, sewn and sold of a model in the period, with the same totals per size. */
public record ModelOutputDto(int productionId, String name, BigDecimal cut, BigDecimal sewn, BigDecimal sold, BigDecimal shipped,
        BigDecimal corrected, List<SizeOutputDto> sizes) {
}
