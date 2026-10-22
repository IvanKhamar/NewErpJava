package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.util.List;

/** supplierId = null: items without a supplier. messageText: a ready order text for a messenger or an email. */
public record SupplierReorderDto(Integer supplierId, String supplierName, String phone, String email,
        List<ReorderLineDto> lines, BigDecimal total, String messageText) {
}
