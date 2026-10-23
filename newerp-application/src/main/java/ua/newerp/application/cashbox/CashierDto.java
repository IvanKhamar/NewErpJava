package ua.newerp.application.cashbox;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A cashier for the list. canFiscalize: Checkbox keys are set for them (or the test stub is on).
 * openShiftId: the open shift, if any.
 */
public record CashierDto(int id, String name, String description, String paymentCode, LocalTime autoCloseTime,
        int warehouseId, String warehouse, int requisitesId, String requisites, boolean isActive,
        boolean canFiscalize, Integer openShiftId, LocalDateTime openShiftStart) {
}
