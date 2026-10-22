package ua.newerp.application.production;

import java.math.BigDecimal;

/** deficit: how many sewn pieces are missing up to minTotal (0 when there are enough). */
public record ProductionModelSummaryDto(int id, String name, String type, int minTotal, BigDecimal totalCut,
        BigDecimal totalSewn, BigDecimal deficit) {
}
