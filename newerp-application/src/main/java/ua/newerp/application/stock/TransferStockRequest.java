package ua.newerp.application.stock;

import java.math.BigDecimal;

/** null from or to: the main stock. */
public record TransferStockRequest(Integer fromWarehouseId, Integer toWarehouseId, BigDecimal quantity, String comment) {
}
