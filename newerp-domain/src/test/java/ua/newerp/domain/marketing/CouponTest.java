package ua.newerp.domain.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CouponTest {

    static final LocalDate START = LocalDate.of(2026, 11, 1);
    static final LocalDate END = LocalDate.of(2026, 11, 30);

    @Test
    void stateDependsOnTheDayAndTheUses() {
        assertThat(Coupon.state(false, START, END, 0, 0, START)).isEqualTo(Coupon.DISABLED);
        assertThat(Coupon.state(true, START, END, 0, 0, START.minusDays(1))).isEqualTo(Coupon.NOT_STARTED);
        assertThat(Coupon.state(true, START, END, 0, 0, END.plusDays(1))).isEqualTo(Coupon.EXPIRED);
        assertThat(Coupon.state(true, START, END, 2, 2, END)).isEqualTo(Coupon.USED_UP);
        assertThat(Coupon.state(true, START, END, 0, 99, END)).isEqualTo(Coupon.ACTIVE); // 0: no limit
        assertThat(Coupon.state(true, null, null, 0, 0, END)).isEqualTo(Coupon.ACTIVE);
    }

    @Test
    void checksTheTerms() {
        var coupon = new Coupon(" Осінь ", "autumn10", Coupon.PERCENT, BigDecimal.TEN, null, false, false, START, END, 0, 1, true);
        assertThat(coupon.code()).isEqualTo("AUTUMN10");
        assertThat(coupon.name()).isEqualTo("Осінь");
        assertThat(coupon.minTotal()).isZero();

        assertThatThrownBy(() -> new Coupon("X", "AUTUMN10", Coupon.PERCENT, new BigDecimal("101"), null, false, false, START, END, 0, 0, true))
                .hasMessage("Знижка у відсотках — не більше 100.");
        assertThatThrownBy(() -> new Coupon("X", "AUTUMN10", Coupon.FIXED, BigDecimal.ZERO, null, false, false, START, END, 0, 0, true))
                .hasMessage("Знижка має бути більшою за нуль (або купон дає безкоштовну доставку).");
        assertThat(new Coupon("X", "SHIPFREE", Coupon.FIXED, BigDecimal.ZERO, null, true, false, START, END, 0, 0, true).freeShipping())
                .isTrue();
        assertThatThrownBy(() -> new Coupon("X", "AUTUMN10", Coupon.FIXED, BigDecimal.ONE, null, false, false, END, START, 0, 0, true))
                .hasMessage("Кінець дії купона раніше за початок.");
        assertThatThrownBy(() -> new Coupon("X", "AUTUMN10", "Z", BigDecimal.ONE, null, false, false, START, END, 0, 0, true))
                .hasMessage("Тип знижки має бути P (відсоток) або F (сума).");
    }
}
