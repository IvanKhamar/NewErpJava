package ua.newerp.domain.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.orders.CustomerRisk;

class CustomerSummaryTest {

    @Test
    void cancelledOrdersCountApartAndAreNotSpent() {
        var first = LocalDateTime.of(2026, 1, 1, 10, 0);
        var last = LocalDateTime.of(2026, 5, 1, 10, 0);
        CustomerSummary summary = CustomerSummary.from(List.of(
                new CustomerOrder(1, last, 5, new BigDecimal("1500")),
                new CustomerOrder(2, first, CustomerRisk.CANCELLED_STATUS_ID, new BigDecimal("700")),
                new CustomerOrder(3, first.plusDays(1), 5, new BigDecimal("300"))));

        assertThat(summary).isEqualTo(new CustomerSummary(3, 1, new BigDecimal("1800"), first, last));
    }

    @Test
    void noOrdersGiveNoDates() {
        assertThat(CustomerSummary.from(List.of())).isEqualTo(new CustomerSummary(0, 0, BigDecimal.ZERO, null, null));
    }

    @Test
    void noteIsTrimmedAndMustNotBeEmpty() {
        assertThat(CustomerNote.validate("  Дзвонити ввечері ")).isEqualTo("Дзвонити ввечері");
        assertThatThrownBy(() -> CustomerNote.validate(" ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CustomerNote.validate("x".repeat(2001))).isInstanceOf(DomainException.class);
    }
}
