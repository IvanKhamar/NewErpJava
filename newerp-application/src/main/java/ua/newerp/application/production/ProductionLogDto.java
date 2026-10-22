package ua.newerp.application.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductionLogDto(int id, int productionId, String productionName, int sizeId, String sizeName, String stage,
        String direction, BigDecimal quantity, BigDecimal stockBefore, BigDecimal stockAfter, String comment, int userId,
        String username, LocalDateTime createdAt) {
}
