package ua.newerp.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

class ProductSorterTest {

    @Test
    void bySalesPutsBestSellersFirstAndKeepsOriginalOrderForTies() {
        assertThat(ProductSorter.bySales(List.of(1, 2, 3, 4, 5), Map.of(3, 5, 4, 9, 5, 5))).containsExactly(4, 3, 5, 1, 2);
    }

    @Test
    void mergePutsPinsOnTheirPositionsAndFillsTheRestFromQueue() {
        assertThat(ProductSorter.merge(List.of(10, 20, 30, 40), List.of(new SortPin(1, 30), new SortPin(3, 99))))
                .containsExactly(30, 10, 99, 20, 40);
    }

    @Test
    void mergeKeepsPinsBeyondTheListAtTheEnd() {
        assertThat(ProductSorter.merge(List.of(10, 20), List.of(new SortPin(10, 7), new SortPin(5, 8)))).containsExactly(10, 20, 8, 7);
    }

    @Test
    void typeCodeRoundTripsAndQueryValuesParse() {
        for (SortGroupType type : SortGroupType.values()) {
            assertThat(SortGroupType.fromCode(type.code())).isEqualTo(type);
        }
        assertThat(SortGroupType.parse("Category")).isEqualTo(SortGroupType.CATEGORY);
        assertThat(SortGroupType.parse("CategoryColor")).isEqualTo(SortGroupType.CATEGORY_COLOR);
        assertThat(SortGroupType.parse("4")).isEqualTo(SortGroupType.CROSS_SELL);
        assertThatThrownBy(() -> SortGroupType.parse("color")).isInstanceOf(DomainException.class);
    }
}
