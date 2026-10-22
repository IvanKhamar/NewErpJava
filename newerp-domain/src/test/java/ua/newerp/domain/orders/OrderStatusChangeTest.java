package ua.newerp.domain.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class OrderStatusChangeTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Test
    void createsChange() {
        assertThat(OrderStatusChange.create(10, 1, 2, 5, NOW)).isEqualTo(new OrderStatusChange(10, 1, 2, 5, NOW));
    }

    @Test
    void rejectsBadChanges() {
        assertThatThrownBy(() -> OrderStatusChange.create(10, 1, 0, 5, NOW)).hasMessage("Статус має бути додатним числом.");
        assertThatThrownBy(() -> OrderStatusChange.create(10, 2, 2, 5, NOW)).hasMessage("Замовлення вже має цей статус.");
        assertThatThrownBy(() -> OrderStatusChange.create(10, 1, 2, 0, NOW)).hasMessage("Не вказано користувача, який змінює статус.");
    }
}
