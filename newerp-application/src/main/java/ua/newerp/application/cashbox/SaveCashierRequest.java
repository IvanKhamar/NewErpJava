package ua.newerp.application.cashbox;

import java.time.LocalTime;

public record SaveCashierRequest(String name, String description, String paymentCode, LocalTime autoCloseTime, int warehouseId,
        int requisitesId, boolean isActive) {
}
