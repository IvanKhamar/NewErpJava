package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.util.List;

public record ReorderReportDto(List<SupplierReorderDto> suppliers, BigDecimal total) {
}
