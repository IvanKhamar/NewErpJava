package ua.newerp.domain.locations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

class ShopLocationTest {

    @Test
    void validateTrimsAndNeedsAName() {
        assertThat(ShopLocation.validate(" Склад ", null, null, "067", "Київ", "", null).name()).isEqualTo("Склад");
        assertThatThrownBy(() -> ShopLocation.validate(" ", "", "", "", "", "", "")).isInstanceOf(DomainException.class);
    }

    @Test
    void importSkipsEmptyRowsAndCollectsAllErrors() {
        LocationImport parsed = ShopLocation.parseImport(List.of(
                ShopLocation.COLUMNS,
                List.of("5", "Склад", "", "", "", "Київ"),
                List.of("", "", "", ""),
                List.of("x", "Без номера"),
                List.of("", "", "опис без назви"),
                List.of("5", "Двійник")));

        assertThat(parsed.rows()).extracting(LocationImportRow::id).containsExactly(5, 5);
        assertThat(parsed.errors()).containsExactly(
                "Рядок 4: ID «x» має бути числом або порожнім.",
                "Рядок 5: Назва має містити від 1 до 64 символів.",
                "ID 5 повторюється в рядках 2, 6.");
    }

    @Test
    void anEmptyFileIsAnError() {
        assertThat(ShopLocation.parseImport(List.of(ShopLocation.COLUMNS)).errors()).containsExactly("У файлі немає рядків для імпорту.");
    }
}
