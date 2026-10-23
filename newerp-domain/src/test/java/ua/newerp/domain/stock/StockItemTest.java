package ua.newerp.domain.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

class StockItemTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    static StockItem fabric(String quantity) {
        StockItem item = new StockItem(null, null, "  Льон  ", "м", new BigDecimal("5"), null, BigDecimal.ZERO);
        if (new BigDecimal(quantity).signum() > 0) {
            item.adjust(new BigDecimal(quantity), "start", 1, "admin", NOW);
        }
        return item;
    }

    @Test
    void trimsTextAndDefaultsUsedFor() {
        StockItem item = fabric("0");
        assertThat(item.getName()).isEqualTo("Льон");
        assertThat(item.getUsedFor()).isEmpty();
        assertThat(item.isActive()).isTrue();
    }

    @Test
    void rejectsEmptyNameAndNegativePrice() {
        assertThatThrownBy(() -> new StockItem(null, null, " ", "м", BigDecimal.ONE, null, BigDecimal.ZERO))
                .isInstanceOf(DomainException.class).hasMessage("Назва позиції: значення обов'язкове.");
        assertThatThrownBy(() -> new StockItem(null, null, "Льон", "м", BigDecimal.ONE, null, new BigDecimal("-1")))
                .hasMessage("Ціна не може бути від'ємною.");
    }

    @Test
    void adjustWritesMovementWithBeforeAndAfter() {
        StockItem item = fabric("10");

        StockMovement movement = item.adjust(new BigDecimal("-3"), " Крій ", 7, "olena", NOW);

        assertThat(item.getQuantity()).isEqualByComparingTo("7");
        assertThat(movement.getAction()).isEqualTo(StockMovement.USE);
        assertThat(movement.getQuantity()).isEqualByComparingTo("3");
        assertThat(movement.getDelta()).isEqualByComparingTo("-3");
        assertThat(movement.getStockBefore()).isEqualByComparingTo("10");
        assertThat(movement.getStockAfter()).isEqualByComparingTo("7");
        assertThat(movement.getComment()).isEqualTo("Крій");
        assertThat(movement.getItemName()).isEqualTo("Льон");
        assertThat(movement.getUserId()).isEqualTo(7);
    }

    @Test
    void adjustCannotGoBelowZeroOrBeZero() {
        StockItem item = fabric("2");
        assertThatThrownBy(() -> item.adjust(new BigDecimal("-2.5"), "x", 1, "a", NOW))
                .hasMessage("Недостатньо залишку: є 2 м, списується 2.5 м.");
        assertThatThrownBy(() -> item.adjust(BigDecimal.ZERO, "x", 1, "a", NOW))
                .hasMessage("Зміна залишку не може дорівнювати нулю.");
        assertThatThrownBy(() -> item.adjust(BigDecimal.ONE, " ", 1, "a", NOW))
                .hasMessage("Причина: значення обов'язкове.");
    }

    @Test
    void takeUpToTakesWhatThereIs() {
        StockItem item = fabric("2");

        StockMovement movement = item.takeUpTo(new BigDecimal("5"), "Крій", 1, "a", NOW);

        assertThat(movement.getQuantity()).isEqualByComparingTo("2");
        assertThat(item.getQuantity()).isEqualByComparingTo("0");
        assertThat(item.takeUpTo(BigDecimal.ONE, "Крій", 1, "a", NOW)).isNull();
    }

    @Test
    void belowMinimum() {
        assertThat(fabric("4").isBelowMinimum()).isTrue();
        assertThat(fabric("5").isBelowMinimum()).isFalse();
    }

    @Test
    void onlyAnEmptyItemIsArchived() {
        StockItem item = fabric("2");
        assertThatThrownBy(item::archive).hasMessageContaining("на складі ще 2 м");
        assertThat(item.isActive()).isTrue();

        item.takeUpTo(new BigDecimal("2"), "Крій", 1, "a", NOW);
        item.archive();
        assertThat(item.isActive()).isFalse();
    }

    @Test
    void supplierAndCategoryAreRenamed() {
        var supplier = new StockSupplier("ТексМаркет", "", "", "", "");
        supplier.update("ТексМаркет Україна", "+380441112233", "", "тканини", "");
        assertThat(supplier.getName()).isEqualTo("ТексМаркет Україна");
        assertThat(supplier.getPhone()).isEqualTo("+380441112233");
        assertThatThrownBy(() -> supplier.update(" ", "", "", "", "")).isInstanceOf(DomainException.class);

        var category = new StockCategory("Тканини", 1);
        category.update("Тканини й трикотаж", 3);
        assertThat(category.getName()).isEqualTo("Тканини й трикотаж");
        assertThat(category.getSortOrder()).isEqualTo(3);
    }
}
