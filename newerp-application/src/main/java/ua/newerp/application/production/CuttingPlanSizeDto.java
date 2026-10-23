package ua.newerp.application.production;

import java.math.BigDecimal;

/** target: the size's part of the model minimum; ordered: what active orders still miss; toCut: whole pieces to cut. */
public record CuttingPlanSizeDto(int sizeId, String name, BigDecimal percent, int target, BigDecimal ordered, BigDecimal cutStock,
        BigDecimal sewnStock, BigDecimal toCut) {
}
