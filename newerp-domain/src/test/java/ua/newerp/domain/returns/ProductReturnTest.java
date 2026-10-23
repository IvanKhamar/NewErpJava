package ua.newerp.domain.returns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

class ProductReturnTest {

    private static final List<ReturnableLine> LINES = List.of(new ReturnableLine(10, "Сукня", "T-1", 2), new ReturnableLine(11, "Шарф", "S", 1));

    @Test
    void returnsNoMoreThanBoughtMinusReturned() {
        assertThat(validate(10, 1, 1)).isEqualTo(LINES.get(0));
        assertThatThrownBy(() -> validate(10, 2, 1)).isInstanceOf(DomainException.class).hasMessage("Кількість має бути від 1 до 1.");
        assertThatThrownBy(() -> validate(11, 1, 1)).hasMessageContaining("вже повернено повністю");
        assertThatThrownBy(() -> validate(12, 1, 0)).hasMessage("Цього товару немає в замовленні.");
    }

    @Test
    void emailIsOptionalButAReasonIsRequired() {
        assertThat(ProductReturn.validate("Олена", "", "0501112233", "", LINES, 10, 1, 1, null, 0)).isNotNull();
        assertThatThrownBy(() -> ProductReturn.validate("Олена", "", "0501112233", "", LINES, 10, 1, 0, null, 0))
                .hasMessage("Оберіть причину повернення.");
        assertThatThrownBy(() -> ProductReturn.validate(" ", "", "0501112233", "", LINES, 10, 1, 1, null, 0))
                .isInstanceOf(DomainException.class);
    }

    private static ReturnableLine validate(int productId, int quantity, int alreadyReturned) {
        return ProductReturn.validate("Олена", "Коваль", "0501112233", "o@example.com", LINES, productId, quantity, 1, "", alreadyReturned);
    }
}
