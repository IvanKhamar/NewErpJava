package ua.newerp.domain.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class SewingPlanTest {

    static final int SIZE = 7;

    static SewingDemand demand(int orderId, int sizeId, int ordered, int held, LocalDate deadline) {
        return new SewingDemand(orderId, sizeId, BigDecimal.valueOf(ordered), BigDecimal.valueOf(held), deadline);
    }

    static BigDecimal n(int value) {
        return BigDecimal.valueOf(value);
    }

    @Test
    void freeSewnStockCoversFirstThenTheCutStockAndTheRestIsToCut() {
        SewingPlanLine line = SewingPlan.forSize(SIZE, n(1), n(2), List.of(demand(1, SIZE, 3, 0, null), demand(2, SIZE, 2, 0, null)));

        assertThat(List.of(line.ordered(), line.missing(), line.toSew(), line.fromCut(), line.toCut()))
                .usingElementComparator(BigDecimal::compareTo).containsExactly(n(5), n(5), n(4), n(2), n(2));
    }

    @Test
    void whatAnOrderAlreadyHoldsIsNotMissing() {
        SewingPlanLine line = SewingPlan.forSize(SIZE, n(0), n(0), List.of(demand(1, SIZE, 3, 2, null), demand(2, SIZE, 1, 1, null)));

        assertThat(List.of(line.ordered(), line.missing(), line.toSew())).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(n(4), n(1), n(1));
        assertThat(line.orderIds()).containsExactly(1);
    }

    @Test
    void enoughSewnMeansNothingToSewAndNegativeStockIsIgnored() {
        assertThat(SewingPlan.forSize(SIZE, n(5), n(0), List.of(demand(1, SIZE, 3, 0, null))).toSew()).isEqualByComparingTo("0");
        SewingPlanLine line = SewingPlan.forSize(SIZE, n(-2), n(-1), List.of(demand(1, SIZE, 3, 0, null)));
        assertThat(line.toSew()).isEqualByComparingTo("3");
        assertThat(line.fromCut()).isEqualByComparingTo("0");
    }

    @Test
    void theDeadlineIsTheEarliestAmongOrdersStillMissingAndOtherSizesAreIgnored() {
        SewingPlanLine line = SewingPlan.forSize(SIZE, n(0), n(0), List.of(
                demand(1, SIZE, 1, 1, LocalDate.of(2026, 10, 1)), // already has everything
                demand(2, SIZE, 1, 0, LocalDate.of(2026, 10, 9)),
                demand(3, SIZE, 1, 0, null),
                demand(4, SIZE + 1, 5, 0, LocalDate.of(2026, 9, 1))));

        assertThat(line.deadline()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(line.orderIds()).containsExactly(2, 3);
        assertThat(line.ordered()).isEqualByComparingTo("3");
    }

    @Test
    void theDeadlineIsTheOrderDatePlusTheCategoryDaysAndZeroMeansNotSet() {
        assertThat(SewingPlan.deadline(LocalDateTime.of(2026, 10, 1, 23, 30), 14)).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(SewingPlan.deadline(LocalDateTime.of(2026, 10, 1, 0, 0), 0)).isNull();
    }
}
