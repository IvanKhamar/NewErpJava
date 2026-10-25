package ua.newerp.domain.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class GiftVoucherTest {

    @Test
    void cleansAndChecksTheForm() {
        GiftVoucher voucher = GiftVoucher.of(" vidro2026 ", null, null, " Олена ", "", null, " З днем народження! ", new BigDecimal("1000.50"), null);

        assertThat(voucher).isEqualTo(new GiftVoucher("VIDRO2026", "", "", "Олена", "", 0, "З днем народження!", new BigDecimal("1000.50"), true));
        assertThatThrownBy(() -> GiftVoucher.of("TOO-LONG-CODE", "", "", "Олена", "", 0, "", BigDecimal.TEN, true))
                .hasMessage("Код сертифіката — від 1 до 10 латинських літер або цифр.");
        assertThatThrownBy(() -> GiftVoucher.of("ПОДАРУНОК", "", "", "Олена", "", 0, "", BigDecimal.TEN, true)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> GiftVoucher.of("ABC", "", "", "Олена", "", 0, "", new BigDecimal("0"), true))
                .hasMessage("Сума сертифіката — більше 0, не більше двох знаків після коми.");
        assertThatThrownBy(() -> GiftVoucher.of("ABC", "", "", "Олена", "", 0, "", new BigDecimal("10.005"), true))
                .hasMessage("Сума сертифіката — більше 0, не більше двох знаків після коми.");
        assertThatThrownBy(() -> GiftVoucher.of("ABC", "Іван", "", " ", "", 0, "", BigDecimal.TEN, true)).hasMessage("Вкажіть, кому сертифікат.");
    }

    @Test
    void generatedCodesAvoidLookAlikes() {
        String code = GiftVoucher.newCode(RandomGenerator.of("L64X128MixRandom"));

        assertThat(code).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void balanceAndUsability() {
        assertThat(GiftVoucher.balance(new BigDecimal("1000"), new BigDecimal("-350"))).isEqualByComparingTo("650");
        assertThat(GiftVoucher.balance(new BigDecimal("1000"), null)).isEqualByComparingTo("1000");

        assertThat(GiftVoucher.usable(true, 0, false)).isTrue(); // made in the admin, no order behind it
        assertThat(GiftVoucher.usable(true, 2001, false)).isFalse(); // bought in an order that is not paid yet
        assertThat(GiftVoucher.usable(true, 2001, true)).isTrue();
        assertThat(GiftVoucher.usable(false, 0, true)).isFalse();
    }
}
