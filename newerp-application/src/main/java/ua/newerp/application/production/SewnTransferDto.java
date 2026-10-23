package ua.newerp.application.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A move of sewn pieces between places; a null warehouse id is the main stock. */
public record SewnTransferDto(int id, int sizeId, String sizeName, Integer fromWarehouseId, String fromName, Integer toWarehouseId,
        String toName, BigDecimal quantity, String comment, String username, LocalDateTime createdAt) {
}
