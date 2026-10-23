package ua.newerp.domain.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CuttingPlanTest {

    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    static CuttingPlan.SizeInput size(int id, String percent, int cut, int sewn, int ordered) {
        return new CuttingPlan.SizeInput(id, n(percent), BigDecimal.valueOf(cut), BigDecimal.valueOf(sewn), BigDecimal.valueOf(ordered));
    }

    @Test
    void theMinimumIsSplitByTheSharesAndThePartsAlwaysAddUpToTheTotal() {
        assertThat(CuttingPlan.split(50, List.of(n("20"), n("30"), n("30"), n("20")))).containsExactly(10, 15, 15, 10);
        // 10 × 1/3 = 3.33 each: the one piece left goes to the first size
        assertThat(CuttingPlan.split(10, List.of(n("1"), n("1"), n("1")))).containsExactly(4, 3, 3);
        // 7 × 25/45 = 3.89, 7 × 20/45 = 3.11: the larger remainder gets the extra piece
        assertThat(CuttingPlan.split(7, List.of(n("25"), n("20")))).containsExactly(4, 3);
    }

    @Test
    void sharesNotSetMeanAnEvenSplitAndAZeroShareGetsNothingWhenOthersAreSet() {
        assertThat(CuttingPlan.split(9, List.of(n("0"), n("0"), n("0")))).containsExactly(3, 3, 3);
        assertThat(CuttingPlan.split(9, List.of(n("50"), n("0"), n("50")))).containsExactly(5, 0, 4);
        assertThat(CuttingPlan.split(0, List.of(n("50"), n("50")))).containsExactly(0, 0);
        assertThat(CuttingPlan.split(5, List.of())).isEmpty();
    }

    @Test
    void toCutIsTargetPlusOrdersMinusCutAndSewnStock() {
        List<CuttingPlan.SizeLine> lines = CuttingPlan.forModel(20, List.of(
                size(1, "50", 2, 3, 4),   // 10 + 4 − 2 − 3 = 9
                size(2, "50", 0, 15, 0),  // 10 − 15: enough already
                size(3, "0", -1, -2, 3))); // sold ahead: only what orders miss counts

        assertThat(lines).extracting(CuttingPlan.SizeLine::target).containsExactly(10, 10, 0);
        assertThat(lines).extracting(CuttingPlan.SizeLine::toCut).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(n("9"), n("0"), n("3"));
    }

    @Test
    void fractionalStockIsRoundedUpToWholePieces() {
        assertThat(CuttingPlan.forModel(5, List.of(new CuttingPlan.SizeInput(1, n("100"), n("1.5"), n("0"), n("0")))).getFirst().toCut())
                .isEqualByComparingTo("4");
    }

    @Test
    void consumablesFollowTheCuttingBomWithSizeRowsOverridingGeneralOnes() {
        var front = new ProductionComponent(1, "Пілочка", 1);
        front.setBom(List.of(
                new ProductionComponent.BomRow(null, 100, n("0.5")),  // fabric for every size
                new ProductionComponent.BomRow(20, 100, n("0.7"))),  // more fabric for size 20
                List.of(10, 20));
        var sleeve = new ProductionComponent(1, "Рукав", 2);
        sleeve.setBom(List.of(new ProductionComponent.BomRow(null, 100, n("0.2")), new ProductionComponent.BomRow(null, 200, n("1"))),
                List.of(10, 20));

        Map<Integer, BigDecimal> need = CuttingPlan.consumables(List.of(front, sleeve), Map.of(10, n("2"), 20, n("1"), 30, n("0")));

        // fabric: 2 × (0.5 + 0.2) + 1 × (0.7 + 0.2) = 2.3; item 200: 2 + 1 = 3
        assertThat(need.get(100)).isEqualByComparingTo("2.3");
        assertThat(need.get(200)).isEqualByComparingTo("3");
        assertThat(need).hasSize(2);
    }
}
