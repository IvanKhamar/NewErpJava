package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** delta: minus = use, plus = receipt. reason is the log comment. */
public record StockMovementDto(int id, int itemId, BigDecimal delta, BigDecimal quantityBefore, BigDecimal quantityAfter,
        String reason, int userId, String username, LocalDateTime createdAt) {
}
