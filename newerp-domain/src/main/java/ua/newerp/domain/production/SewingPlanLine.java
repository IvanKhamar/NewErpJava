package ua.newerp.domain.production;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A line of the sewing plan for a size. missing: what active orders miss; toSew: what to sew after the free sewn stock;
 * fromCut: how much of it is already cut; toCut: how much still has to be cut.
 */
public record SewingPlanLine(int sizeId, BigDecimal ordered, BigDecimal missing, BigDecimal toSew, BigDecimal fromCut, BigDecimal toCut,
        LocalDate deadline, List<Integer> orderIds) {
}
