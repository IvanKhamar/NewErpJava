package ua.newerp.application.production;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** missing: what active orders miss; toSew: what to sew after the free stock; toCut: what still has to be cut. */
public record SewingPlanRowDto(int productionId, String modelName, int sizeId, String sizeName, BigDecimal ordered, BigDecimal missing,
        BigDecimal sewnStock, BigDecimal cutStock, BigDecimal toSew, BigDecimal fromCut, BigDecimal toCut, LocalDate deadline, boolean overdue,
        List<Integer> orderIds) {
}
