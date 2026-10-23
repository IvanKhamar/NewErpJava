package ua.newerp.application.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class StockReportServiceTest {

    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    static StockItemDto item(int id, Integer categoryId, String categoryName, String qty, String price) {
        return new StockItemDto(id, "Позиція " + id, "м", categoryId, categoryName, null, null, n(qty), BigDecimal.ZERO, false, "",
                n(price));
    }

    @Test
    void valueIsQuantityTimesPriceWithNegativeStockAsNone() {
        List<CategoryValueDto> values = StockReportService.values(List.of(
                item(1, null, null, "4", "10"),
                item(2, 1, "Фурнітура", "10", "2"),
                item(3, 2, "Тканини", "2.5", "100"),
                item(4, 2, "Тканини", "-3", "100")));

        assertThat(values).extracting(CategoryValueDto::name).containsExactly("Тканини", "Фурнітура", StockReportService.NO_CATEGORY_NAME);
        assertThat(values.getFirst().value()).isEqualByComparingTo("250");
        assertThat(values.getFirst().items()).isEqualTo(2);
        assertThat(values.getLast().value()).isEqualByComparingTo("40");
    }

    @Test
    void paceGivesDaysLeftAndWhatToOrderForTheCover() {
        var flow = new StockReportSource.ItemFlow(1, n("9"), n("0"), n("0"));
        // 9 in 30 days = 0.3 a day; 4 on stock last 13 days; 60 days need 18 − 4 − 2 on order = 12
        ItemConsumptionDto line = StockReportService.consumption(item(1, null, null, "4", "10"), flow, n("2"), 30, 60);

        assertThat(line.perDay()).isEqualByComparingTo("0.3");
        assertThat(line.daysLeft()).isEqualTo(13);
        assertThat(line.suggested()).isEqualByComparingTo("12");
        assertThat(line.sum()).isEqualByComparingTo("120");
    }

    @Test
    void nothingUsedMeansNoPaceAndNothingToOrder() {
        var flow = new StockReportSource.ItemFlow(1, n("0"), n("5"), n("-1"));
        ItemConsumptionDto line = StockReportService.consumption(item(1, null, null, "-2", "10"), flow, BigDecimal.ZERO, 30, 30);

        assertThat(line.daysLeft()).isNull();
        assertThat(line.suggested()).isEqualByComparingTo("0");
    }

    @Test
    void aModelSumsItsSizes() {
        List<ModelOutputDto> output = StockReportService.output(List.of(
                new StockReportSource.SizeFlow(1, "Сукня", 1, "S", n("5"), n("3"), n("1"), n("0"), n("0")),
                new StockReportSource.SizeFlow(1, "Сукня", 2, "M", n("2"), n("4"), n("2"), n("1"), n("-1")),
                new StockReportSource.SizeFlow(2, "Футболка", 3, "L", n("0"), n("10"), n("4"), n("0"), n("0"))));

        assertThat(output).extracting(ModelOutputDto::name).containsExactly("Сукня", "Футболка");
        assertThat(output.getFirst().cut()).isEqualByComparingTo("7");
        assertThat(output.getFirst().sold()).isEqualByComparingTo("3");
        assertThat(output.getFirst().corrected()).isEqualByComparingTo("-1");
        assertThat(output.getFirst().sizes()).extracting(SizeOutputDto::name).containsExactly("S", "M");
    }
}
