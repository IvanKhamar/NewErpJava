package ua.newerp.application.production;

import java.math.BigDecimal;
import java.util.List;

public record CuttingPlanModelDto(int productionId, String name, int minTotal, BigDecimal toCut, List<CuttingPlanSizeDto> sizes) {
}
