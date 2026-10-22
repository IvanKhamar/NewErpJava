package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseOrderSummaryDto(int id, int supplierId, String supplierName, String status, LocalDateTime createdAt,
        int lineCount, BigDecimal total) {
}
