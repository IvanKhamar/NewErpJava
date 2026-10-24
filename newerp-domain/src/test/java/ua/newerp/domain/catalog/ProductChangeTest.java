package ua.newerp.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ProductChangeTest {

    final Set<Integer> labels = Set.of(1, 2);
    final Set<Integer> categories = Set.of(30, 31);

    @Test
    void nullPartsStayAsTheyAre() {
        ProductChange change = ProductChange.of(null, null, null, null, labels, categories);

        assertThat(change).isEqualTo(new ProductChange(null, null, null, null));
    }

    @Test
    void priceIsNotNegativeWithTwoDecimals() {
        assertThat(ProductChange.of(new BigDecimal("1499.90"), null, null, null, labels, categories).price()).isEqualByComparingTo("1499.9");
        assertThat(ProductChange.of(new BigDecimal("1500.0000"), null, null, null, labels, categories).price()).isEqualByComparingTo("1500");
        assertThatThrownBy(() -> ProductChange.of(new BigDecimal("-1"), null, null, null, labels, categories))
                .hasMessage("Ціна — не менше 0, не більше двох знаків після коми.");
        assertThatThrownBy(() -> ProductChange.of(new BigDecimal("10.005"), null, null, null, labels, categories))
                .hasMessage("Ціна — не менше 0, не більше двох знаків після коми.");
    }

    @Test
    void labelsAndCategoriesMustBeKnownAndCategoriesNotEmpty() {
        ProductChange change = ProductChange.of(null, false, List.of(2, 2), List.of(31), labels, categories);
        assertThat(change.labelIds()).containsExactly(2);
        assertThat(change.categoryIds()).containsExactly(31);
        assertThat(ProductChange.of(null, null, List.of(), null, labels, categories).labelIds()).isEmpty();

        assertThatThrownBy(() -> ProductChange.of(null, null, List.of(9), null, labels, categories)).hasMessage("Такої мітки немає.");
        assertThatThrownBy(() -> ProductChange.of(null, null, null, List.of(), labels, categories))
                .hasMessage("Оберіть хоча б одну категорію.");
        assertThatThrownBy(() -> ProductChange.of(null, null, null, List.of(32), labels, categories))
                .hasMessage("Такої категорії немає або вона вимкнена.");
    }
}
