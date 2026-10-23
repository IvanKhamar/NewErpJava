package ua.newerp.domain.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import ua.newerp.domain.common.DomainException;

class LiqPayPaymentTest {

    static final BigDecimal TOTAL = new BigDecimal("1500");

    @Test
    void theAmountIsTheOrderTotalByDefaultAndCanBeAPrepayment() {
        assertThat(LiqPayPayment.validateAmount(null, TOTAL)).isEqualByComparingTo("1500");
        assertThat(LiqPayPayment.validateAmount(new BigDecimal("300.126"), TOTAL)).isEqualTo(new BigDecimal("300.13"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "1500.01"})
    void zeroAndMoreThanTheTotalAreRejected(String amount) {
        assertThatThrownBy(() -> LiqPayPayment.validateAmount(new BigDecimal(amount), TOTAL)).isInstanceOf(DomainException.class);
    }

    @Test
    void theDescriptionIsTheOldSitesText() {
        assertThat(LiqPayPayment.description(2045, " Ірина Іваненко ", "+380671112233"))
                .isEqualTo("Оплата замовлення #2045.\nПокупець: Ірина Іваненко. \nТелефон: +380671112233");
    }

    @ParameterizedTest
    @CsvSource({",payment_not_found,NOT_FOUND", "success,,PAID", "sandbox,,PAID", "wait_accept,,PAID", "reversed,,REVERSED",
            "failure,,FAILED", "3ds_verify,,WAITING"})
    void mapsLiqPayStatuses(String status, String error, LiqPayState expected) {
        assertThat(LiqPayPayment.interpret(status, error)).isEqualTo(expected);
    }

    @Test
    void anUnknownErrorIsADomainError() {
        assertThatThrownBy(() -> LiqPayPayment.interpret(null, "invalid_signature")).isInstanceOf(DomainException.class);
    }
}
