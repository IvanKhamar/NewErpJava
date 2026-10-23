package ua.newerp.application.production;

import java.math.BigDecimal;

/** null from or to: the main stock. */
public record SewnTransferRequest(int sizeId, Integer fromWarehouseId, Integer toWarehouseId, BigDecimal quantity, String comment) {
}
