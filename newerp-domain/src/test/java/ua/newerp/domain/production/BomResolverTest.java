package ua.newerp.domain.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BomResolverTest {

    record Row(Integer sizeId, Integer itemId, String label) {
    }

    @Test
    void sizeRowOverridesGeneralRow() {
        List<Row> rows = List.of(
                new Row(null, 1, "general-1"),
                new Row(5, 1, "size5-1"),
                new Row(null, 2, "general-2"),
                new Row(6, 2, "size6-2"),
                new Row(5, null, "no key"));

        List<Row> effective = BomResolver.resolve(rows, 5, Row::sizeId, Row::itemId);

        assertThat(effective).extracting(Row::label).containsExactly("size5-1", "general-2");
    }
}
