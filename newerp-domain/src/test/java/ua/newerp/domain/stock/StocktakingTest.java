package ua.newerp.domain.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

class StocktakingTest {

    static List<String> row(String id, String actual) {
        return List.of(id, "Льон", "Тканини", "м", "10", actual);
    }

    @Test
    void readsCountedRowsAndSkipsTheHeaderAndUncountedRows() {
        var parsed = Stocktaking.parse(List.of(Stocktaking.COLUMNS, row("1", "12,5"), row("2", ""), row("3", "1 250"), row("4", "0")));

        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.counts()).extracting(Stocktaking.Count::itemId).containsExactly(1, 3, 4);
        assertThat(parsed.counts()).extracting(Stocktaking.Count::actual).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("12.5"), new BigDecimal("1250"), BigDecimal.ZERO);
    }

    @Test
    void namesTheExcelRowOfEachError() {
        var parsed = Stocktaking.parse(List.of(Stocktaking.COLUMNS, row("", "3"), row("1", "багато"), row("2", "-1"),
                row("3", "0.0001"), row("4", "1"), row("4", "2"), List.of("5")));

        assertThat(parsed.errors()).containsExactly(
                "Рядок 2: немає ID позиції або він не число.",
                "Рядок 3: «багато» — не кількість.",
                "Рядок 4: кількість має бути від 0, не більше 3 знаків після коми.",
                "Рядок 5: кількість має бути від 0, не більше 3 знаків після коми.",
                "Рядок 7: позиція 4 указана двічі.");
        assertThat(parsed.counts()).extracting(Stocktaking.Count::itemId).containsExactly(4);
    }

    @Test
    void recountWritesTheDifferenceToTheLogAndNothingWhenItMatches() {
        var item = new StockItem(null, null, "Льон", "м", BigDecimal.ONE, "", BigDecimal.ZERO);
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
        item.adjust(BigDecimal.TEN, "Прихід", 1, "keeper", now);

        StockMovement shortage = item.recount(new BigDecimal("7.5"), "Інвентаризація", 1, "keeper", now);

        assertThat(shortage.getDelta()).isEqualByComparingTo("-2.5");
        assertThat(shortage.getStockAfter()).isEqualByComparingTo("7.5");
        assertThat(item.getQuantity()).isEqualByComparingTo("7.5");
        assertThat(item.recount(new BigDecimal("7.500"), "Інвентаризація", 1, "keeper", now)).isNull();
        assertThat(item.recount(new BigDecimal("9"), "Інвентаризація", 1, "keeper", now).getDelta()).isEqualByComparingTo("1.5");
        assertThatThrownBy(() -> item.recount(new BigDecimal("-1"), "Інвентаризація", 1, "keeper", now))
                .isInstanceOf(DomainException.class);
    }
}
