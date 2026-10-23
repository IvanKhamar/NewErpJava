package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A move between places; a null warehouse id is the main stock. */
public record StockTransferDto(int id, Integer fromWarehouseId, String fromName, Integer toWarehouseId, String toName,
        BigDecimal quantity, String comment, String username, LocalDateTime createdAt) {
}
