package ua.newerp.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CrossSellItemTest {

    @Test
    void discountIsAPercentWithTwoDecimals() {
        assertThat(new CrossSellItem(CrossSellItem.PRODUCT, 1, null).discount()).isZero();
        assertThat(CrossSellItem.requireDiscount(new BigDecimal("99.99"))).isEqualByComparingTo("99.99");
        assertThatThrownBy(() -> CrossSellItem.requireDiscount(new BigDecimal("100")))
                .hasMessage("Знижка — від 0 до 99,99 %, не більше двох знаків після коми.");
        assertThatThrownBy(() -> CrossSellItem.requireDiscount(new BigDecimal("1.005"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new CrossSellItem("label", 1, null)).hasMessage("Тип рядка допродажу — product або category.");
        assertThatThrownBy(() -> new CrossSellItem(CrossSellItem.CATEGORY, 0, null)).hasMessage("Оберіть товар або категорію.");
    }
}
