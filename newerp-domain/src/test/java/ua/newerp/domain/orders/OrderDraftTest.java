package ua.newerp.domain.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.newerp.domain.common.DomainException;

class OrderDraftTest {

    static final List<OrderDraftLine> TWO_DRESSES = List.of(line(1, 10, 2, 500));

    @Test
    void totalsAreProductsPlusShippingMinusDiscount() {
        var draft = draft(80, 100, 0, List.of(line(1, 10, 2, 500), line(2, null, 1, 300)));

        assertThat(draft.subTotal()).isEqualByComparingTo("1300");
        assertThat(draft.total()).isEqualByComparingTo("1280");
    }

    @ParameterizedTest
    @CsvSource({"0, 0, 1001, завдатку", "0, 1001, 0, Знижка", "-1, 0, 0, від'ємними"})
    void rejectsWrongAmounts(int shipping, int discount, int prepayment, String message) {
        assertThatThrownBy(() -> draft(shipping, discount, prepayment, TWO_DRESSES))
                .isInstanceOf(DomainException.class).hasMessageContaining(message);
    }

    @Test
    void aPrepaymentEqualToTheTotalIsAllowed() {
        assertThat(draft(0, 0, 1000, TWO_DRESSES).prepayment()).isEqualByComparingTo("1000");
    }

    @Test
    void requiresLinesMethodsAndAContact() {
        List<OrderDraftLine> one = List.of(line(1, null, 1, 1));
        assertThatThrownBy(() -> OrderDraft.create("Тарас", "", "НП", "Готівка", List.of(), null, null, null)).hasMessageContaining("Кошик");
        assertThatThrownBy(() -> OrderDraft.create("Тарас", "", " ", "Готівка", one, null, null, null)).hasMessageContaining("доставки");
        assertThatThrownBy(() -> OrderDraft.create("Тарас", "", "НП", "", one, null, null, null)).hasMessageContaining("оплати");
        assertThatThrownBy(() -> OrderDraft.create("", "", "НП", "Готівка", one, null, null, null)).hasMessageContaining("телефон");
        assertThatThrownBy(() -> OrderDraft.create("", "0671234567", "НП", "Готівка", List.of(line(1, null, 0, 1)), null, null, null))
                .hasMessageContaining("Кількість");
    }

    private static OrderDraft draft(int shipping, int discount, int prepayment, List<OrderDraftLine> lines) {
        return OrderDraft.create("Тарас", "", "Нова Пошта", "Накладений платіж", lines,
                BigDecimal.valueOf(shipping), BigDecimal.valueOf(discount), BigDecimal.valueOf(prepayment));
    }

    private static OrderDraftLine line(int productId, Integer optionValueId, int quantity, int price) {
        return new OrderDraftLine(productId, optionValueId, quantity, BigDecimal.valueOf(price));
    }
}
