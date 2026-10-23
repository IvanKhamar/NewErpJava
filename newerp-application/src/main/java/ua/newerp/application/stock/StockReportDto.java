package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Stock reports for the last {@code days} days (from {@code from} on): stock value by category now, consumption of every
 * consumable that moved, and production output by model and size.
 */
public record StockReportDto(int days, LocalDate from, int coverDays, List<CategoryValueDto> categories, BigDecimal totalValue,
        List<ItemConsumptionDto> consumption, BigDecimal suggestedTotal, List<ModelOutputDto> output) {
}
