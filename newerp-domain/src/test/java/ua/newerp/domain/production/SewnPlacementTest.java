package ua.newerp.domain.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.newerp.domain.production.ProductionModelTest.NOW;
import static ua.newerp.domain.production.ProductionModelTest.n;

import java.util.List;

import org.junit.jupiter.api.Test;

class SewnPlacementTest {

    /** "Сукня": S has 10 sewn, 4 of them at warehouse 7. */
    static SewnPlacement placement() {
        ProductionModel dress = ProductionModelTest.dress();
        dress.recordSewn(1, n("10"), "", 1, "keeper", NOW);
        var placement = new SewnPlacement(dress, List.of());
        placement.move(1, null, 7, n("4"), "у шоурум", 1, "keeper", NOW);
        return placement;
    }

    @Test
    void movesKeepTheSewnStockAndNeverTakeMoreThanThePlaceHas() {
        SewnPlacement placement = placement();

        assertThat(placement.at(1, null)).isEqualByComparingTo("6");
        assertThat(placement.at(1, 7)).isEqualByComparingTo("4");
        assertThat(placement.at(2, 7)).isEqualByComparingTo("0");
        assertThat(placement.model().requireSize(1).getSewnStock()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> placement.move(1, 7, 8, n("5"), "", 1, "keeper", NOW))
                .hasMessageContaining("Розмір S: на цьому складі пошито 4");
        assertThatThrownBy(() -> placement.move(2, null, 7, n("1"), "", 1, "keeper", NOW))
                .hasMessageContaining("на основному складі пошито 0");
    }

    @Test
    void sewnAndShippedAtAWarehouseChangeItAndTheSewnStock() {
        SewnPlacement placement = placement();
        placement.sewnAt(1, 7, n("2"), "", 1, "keeper", NOW);
        ProductionLogEntry shipped = placement.shipFrom(1, 7, n("5"), "фотосесія", 1, "keeper", NOW);

        assertThat(shipped.getStockAfter()).isEqualByComparingTo("7");
        assertThat(placement.at(1, 7)).isEqualByComparingTo("1");
        assertThat(placement.at(1, null)).isEqualByComparingTo("6");
        // the main stock has 6: the warehouse's piece is not shipped from there
        assertThatThrownBy(() -> placement.shipFrom(1, null, n("7"), "", 1, "keeper", NOW))
                .hasMessageContaining("на основному складі пошито 6");
    }

    @Test
    void stocktakingOfAPlaceChangesItAndTheSewnStockBySameDifference() {
        SewnPlacement placement = placement();
        ProductionLogEntry found = placement.recountAt(1, 7, n("5"), "Інвентаризація", 1, "keeper", NOW);

        assertThat(found.getStockAfter()).isEqualByComparingTo("11");
        assertThat(placement.at(1, null)).isEqualByComparingTo("6");
        assertThat(placement.recountAt(1, null, n("6"), "Інвентаризація", 1, "keeper", NOW)).isNull();
        assertThat(placement.isAtWarehouses()).isTrue();
        assertThat(placement.created()).singleElement().extracting(SewnBalance::getWarehouseId).isEqualTo(7);
    }
}
