package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderDto(int id, int supplierId, String supplierName, String supplierPhone, String status,
        String comment, LocalDateTime createdAt, LocalDateTime orderedAt, LocalDateTime closedAt,
        List<PurchaseOrderLineDto> lines, BigDecimal total, String messageText) {
}
