package ua.newerp.domain.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BankPaymentTest {

    static BankPayment payment(String amount, String description) {
        return new BankPayment("tx-1", "UA00", new BigDecimal(amount), new BigDecimal("10000"), description, "",
                LocalDate.of(2026, 10, 10), LocalTime.of(14, 30));
    }

    static OrderCandidate order(int id, String total, String customer, String phone, String prepayment, Integer linked) {
        return new OrderCandidate(id, customer, phone, new BigDecimal(total), new BigDecimal(prepayment),
                LocalDateTime.of(2026, 10, 1, 0, 0).plusDays(id % 10), "2) Прийнято", linked);
    }

    static OrderCandidate order(int id, String total) {
        return order(id, total, "Тарас Клієнт", "+380670000000", "0", null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"1500.00 UAH|1500", "1 234,50 UAH|1234.5", "99|99", "—|0"})
    void parseMoneyReadsTheOldSitesText(String text, BigDecimal expected) {
        assertThat(BankPayment.parseMoney(text)).isEqualByComparingTo(expected);
    }

    @Test
    void aNewPaymentStoresMoneyAsTheOldSiteDoes() {
        BankPayment payment = payment("1500", "");

        assertThat(payment.getAmountText()).isEqualTo("1500.00 UAH");
        assertThat(payment.getRestText()).isEqualTo("10000.00 UAH");
        assertThat(payment.getAmount()).isEqualByComparingTo("1500");
        assertThat(payment.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 10, 10, 14, 30));
    }

    @Test
    void orderNumbersButNotAmounts() {
        assertThat(PaymentMatcher.orderNumbersIn("Оплата зам.№12345, 777 шт; сума 1500.00 грн, 2,50")).containsExactly(12345, 777);
    }

    @Test
    void orderNumberAndPhoneBeatTheSameAmount() {
        BankPayment payment = payment("1500", "Оплата замовлення 2045 від Коваленко, тел 067 000 00 99");
        OrderCandidate sameAmount = order(2001, "1500");
        OrderCandidate byNumber = order(2045, "1450", "Ірина Коваленко", "+380670000099", "0", null);
        OrderCandidate paidAlready = order(2002, "1500", "Тарас Клієнт", "+380670000000", "0", 5);

        List<ScoredCandidate> ranked = PaymentMatcher.rank(payment, List.of(sameAmount, paidAlready, byNumber));

        assertThat(ranked).extracting(r -> r.order().orderId()).containsExactly(2045, 2001, 2002);
        assertThat(ranked.get(0).reasons()).isEqualTo(MatchReason.ORDER_NUMBER | MatchReason.PHONE | MatchReason.NAME);
        assertThat(ranked.get(1).reasons()).isEqualTo(MatchReason.TOTAL);
    }

    @Test
    void recognisesThePrepaymentAndTheRest() {
        BankPayment payment = payment("300", "Передоплата");

        List<ScoredCandidate> ranked = PaymentMatcher.rank(payment, List.of(
                order(1, "1500", "Тарас Клієнт", "+380670000000", "300", null),
                order(2, "1500", "Тарас Клієнт", "+380670000000", "1200", null)));

        assertThat(ranked).allSatisfy(r -> assertThat(r.reasons()).isEqualTo(MatchReason.PREPAYMENT));
    }
}
