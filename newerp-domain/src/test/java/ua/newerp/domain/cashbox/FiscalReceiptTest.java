package ua.newerp.domain.cashbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.newerp.domain.common.DomainException;

class FiscalReceiptTest {

    static final ReceiptSourceLine DRESS = new ReceiptSourceLine(101, " Сукня лляна ", new BigDecimal("750"), 2);
    static final ReceiptSourceLine GIFT = new ReceiptSourceLine(102, "Подарунок", BigDecimal.ZERO, 1);

    @Test
    void kopecksAndThousandthsAndFreeItemsSkippedLikeOldSite() {
        FiscalReceipt receipt = FiscalReceipt.build(42, "Олена", List.of(DRESS, GIFT), new BigDecimal("1500"), "liqpay", "LiqPay", " a@b.ua ");

        assertThat(receipt.goods()).containsExactly(new ReceiptGood("101", "Сукня лляна", 75000, 2000));
        assertThat(receipt.payment()).isEqualTo(new ReceiptPayment(ReceiptPaymentType.CASHLESS, "LiqPay", 150000));
        assertThat(receipt.discountKopecks()).isZero();
        assertThat(receipt.footer()).isEqualTo("Замовлення № 42");
        assertThat(receipt.email()).isEqualTo("a@b.ua");
    }

    @Test
    void sumBelowGoodsAddsDiscountForTheDifference() {
        // A prepayment of 300 is already received: the receipt is for 1200, the difference is a discount, as on the old site.
        FiscalReceipt receipt = FiscalReceipt.build(42, "Олена", List.of(DRESS), new BigDecimal("1200"), "bank_transfer", "", null);

        assertThat(receipt.discountKopecks()).isEqualTo(30000);
        assertThat(receipt.payment().valueKopecks()).isEqualTo(120000);
        assertThat(receipt.email()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "cod|<b>Оплата</b> при отриманні|CASHLESS|Оплата в Nova pay",
            "cash|Готівкою &quot;в магазині&quot; <img src=x>|CASH|Готівкою \"в магазині\"",
            "free_checkout|''|CASH|Готівка"})
    void paymentTypeAndLabel(String code, String method, ReceiptPaymentType type, String label) {
        FiscalReceipt receipt = FiscalReceipt.build(1, "Олена", List.of(DRESS), new BigDecimal("1500"), code, method, null);

        assertThat(receipt.payment().type()).isEqualTo(type);
        assertThat(receipt.payment().label()).isEqualTo(label);
    }

    @Test
    void rejectsWhatCheckboxWouldReject() {
        // no products with a price; everything paid by the prepayment; paid delivery on top
        assertThatThrownBy(() -> FiscalReceipt.build(1, "О", List.of(GIFT), new BigDecimal("100"), "cod", "", null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> FiscalReceipt.build(1, "О", List.of(DRESS), BigDecimal.ZERO, "cod", "", null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> FiscalReceipt.build(1, "О", List.of(DRESS), new BigDecimal("1570"), "cod", "", null))
                .isInstanceOf(DomainException.class).hasMessageContaining("(1570.00) більша за суму товарів (1500.00)");
    }
}
