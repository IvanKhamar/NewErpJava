package ua.newerp.application.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ReorderServiceTest {

    static StockItemDto item(int id, String name, Integer supplierId, String qty, String min, String price) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal m = new BigDecimal(min);
        return new StockItemDto(id, name, "м", null, null, supplierId, null, q, m, q.compareTo(m) < 0, "", new BigDecimal(price));
    }

    @Test
    void suggestsUpToFactorTimesMinimumRoundedUpMinusOnOrder() {
        var suppliers = Map.of(1, new StockSupplierDto(1, "Б-постачальник", "+380", "", "", ""),
                2, new StockSupplierDto(2, "А-постачальник", "", "", "", ""));
        var items = List.of(
                item(1, "Льон", 1, "2.5", "10", "100"),   // 20 - 2.5 - 3 = 14.5 → 15
                item(2, "Бавовна", 1, "50", "10", "1"),  // not below minimum
                item(3, "Ґудзики", 2, "0", "4", "2"),    // 8 - 0 - 8 = 0 → left out
                item(4, "Нитки", null, "1", "2", "10")); // 4 - 1 = 3

        ReorderReportDto report = ReorderService.build(items, suppliers,
                Map.of(1, new BigDecimal("3"), 3, new BigDecimal("8")), new BigDecimal("2"));

        assertThat(report.suppliers()).extracting(SupplierReorderDto::supplierName)
                .containsExactly("Б-постачальник", ReorderService.NO_SUPPLIER_NAME);
        ReorderLineDto len = report.suppliers().get(0).lines().get(0);
        assertThat(len.suggested()).isEqualByComparingTo("15");
        assertThat(len.onOrder()).isEqualByComparingTo("3");
        assertThat(len.sum()).isEqualByComparingTo("1500");
        assertThat(report.total()).isEqualByComparingTo("1530");
    }

    @Test
    void messageText() {
        var line = new ReorderLineDto(1, "Льон", "м", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("2.5000"), BigDecimal.ONE, BigDecimal.ONE);
        assertThat(ReorderService.messageText(List.of(line))).isEqualTo("Добрий день! Хочемо замовити:\n— Льон: 2.5 м\nДякуємо!");
    }
}
