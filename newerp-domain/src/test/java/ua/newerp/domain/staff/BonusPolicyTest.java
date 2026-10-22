package ua.newerp.domain.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class BonusPolicyTest {

    @Test
    void percentOfCountedOrdersPerManagerSortedByBonus() {
        BonusPolicy policy = new BonusPolicy(new BigDecimal("1.5"), List.of(20, 22));

        List<ManagerBonus> bonuses = policy.calculate(List.of(
                new BonusOrder(1, 7, 20, new BigDecimal("1000")),
                new BonusOrder(2, 8, 22, new BigDecimal("3000.30")),
                new BonusOrder(3, 7, 22, new BigDecimal("500")),
                new BonusOrder(4, 7, 5, new BigDecimal("9999"))));

        assertThat(bonuses).extracting(ManagerBonus::managerId).containsExactly(8, 7);
        assertThat(bonuses.get(0).bonus()).isEqualByComparingTo("45.00"); // 45.0045 → 45.00
        assertThat(bonuses.get(1).ordersCount()).isEqualTo(2);
        assertThat(bonuses.get(1).revenue()).isEqualByComparingTo("1500");
        assertThat(bonuses.get(1).bonus()).isEqualByComparingTo("22.50");
    }

    @Test
    void roundsHalfAwayFromZero() {
        BonusPolicy policy = new BonusPolicy(BigDecimal.ONE, List.of(1));
        assertThat(policy.calculate(List.of(new BonusOrder(1, 1, 1, new BigDecimal("0.5")))).get(0).bonus())
                .isEqualByComparingTo("0.01"); // 0.005 → 0.01
    }

    @Test
    void validatesSettings() {
        assertThatThrownBy(() -> new BonusPolicy(new BigDecimal("101"), List.of(1)))
                .hasMessage("Відсоток бонусу має бути від 0 до 100.");
        assertThatThrownBy(() -> new BonusPolicy(BigDecimal.ONE, List.of()))
                .hasMessage("Вкажіть хоча б один статус, за який нараховується бонус.");
    }
}
