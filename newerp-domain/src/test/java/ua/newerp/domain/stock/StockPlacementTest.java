package ua.newerp.domain.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

class StockPlacementTest {

    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    /** 10 m in total: 4 at warehouse 7, so 6 on the main stock. */
    static StockPlacement placement() {
        var placement = new StockPlacement(StockItemTest.fabric("10"), List.of());
        placement.move(null, 7, n("4"), "", 1, "keeper", StockItemTest.NOW);
        return placement;
    }

    @Test
    void theMainStockIsWhatIsNotAtAWarehouse() {
        StockPlacement placement = placement();

        assertThat(placement.main()).isEqualByComparingTo("6");
        assertThat(placement.at(7)).isEqualByComparingTo("4");
        assertThat(placement.at(8)).isEqualByComparingTo("0");
        assertThat(placement.item().getQuantity()).isEqualByComparingTo("10");
        assertThat(placement.created()).singleElement().extracting(StockBalance::getWarehouseId).isEqualTo(7);
        assertThat(placement.isAtWarehouses()).isTrue();
    }

    @Test
    void movesBetweenWarehousesKeepTheTotalAndNeverTakeMoreThanTheSourceHas() {
        StockPlacement placement = placement();
        StockTransfer transfer = placement.move(7, 8, n("1.5"), "на пошив", 1, "keeper", StockItemTest.NOW);

        assertThat(transfer.getFromWarehouseId()).isEqualTo(7);
        assertThat(transfer.getToWarehouseId()).isEqualTo(8);
        assertThat(placement.at(7)).isEqualByComparingTo("2.5");
        assertThat(placement.at(8)).isEqualByComparingTo("1.5");
        assertThat(placement.main()).isEqualByComparingTo("6");

        assertThatThrownBy(() -> placement.move(7, null, n("3"), "", 1, "keeper", StockItemTest.NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("на цьому складі є 2.5 м");
        assertThatThrownBy(() -> placement.move(null, 7, n("7"), "", 1, "keeper", StockItemTest.NOW))
                .hasMessageContaining("на основному складі є 6 м");
        assertThatThrownBy(() -> placement.move(7, 7, n("1"), "", 1, "keeper", StockItemTest.NOW))
                .hasMessageContaining("різне місце");
        assertThatThrownBy(() -> placement.move(null, 7, n("0.0001"), "", 1, "keeper", StockItemTest.NOW))
                .hasMessageContaining("3 знаків");
    }

    @Test
    void useAndReceiptAtAWarehouseChangeItAndTheTotal() {
        StockPlacement placement = placement();
        StockMovement use = placement.adjustAt(7, n("-3"), "Крій", 1, "keeper", StockItemTest.NOW);

        assertThat(use.getDelta()).isEqualByComparingTo("-3");
        assertThat(placement.at(7)).isEqualByComparingTo("1");
        assertThat(placement.item().getQuantity()).isEqualByComparingTo("7");
        assertThat(placement.main()).isEqualByComparingTo("6");

        placement.adjustAt(null, n("2"), "Прихід", 1, "keeper", StockItemTest.NOW);
        assertThat(placement.main()).isEqualByComparingTo("8");
        // the total has 9, but warehouse 7 only 1: use there cannot take from the main stock
        assertThatThrownBy(() -> placement.adjustAt(7, n("-2"), "Крій", 1, "keeper", StockItemTest.NOW))
                .hasMessageContaining("на цьому складі є 1 м");
    }

    @Test
    void whenTheTotalWentDownElsewhereTheMainStockIsShortFirst() {
        StockPlacement placement = placement();
        placement.item().takeUpTo(n("8"), "Замовлення #5", 1, "site", StockItemTest.NOW);

        assertThat(placement.main()).isEqualByComparingTo("-2");
        assertThatThrownBy(() -> placement.adjustAt(null, n("-1"), "Крій", 1, "keeper", StockItemTest.NOW))
                .hasMessageContaining("на основному складі є 0 м");
    }
}
