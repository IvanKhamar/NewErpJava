package ua.newerp.domain.partners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ua.newerp.domain.common.DomainException;

class PartnerTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 12, 0);

    static Partner partner(String code, int defaultQuantity) {
        return new Partner(new PartnerSettings(code, "EVA", true, "", true, "Vidro", "", defaultQuantity), NOW);
    }

    static Partner partner() {
        return partner("eva", 5);
    }

    static CategoryMapping category(int categoryId, String size, String externalId, String externalName, Integer supply, Integer sale) {
        return new CategoryMapping(categoryId, size, externalId, externalName, supply == null ? null : BigDecimal.valueOf(supply),
                sale == null ? null : BigDecimal.valueOf(sale));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ева", "eva feed", "a23456789012345678901234567890123"})
    void theCodeMustBeShortLatin(String code) {
        assertThatThrownBy(() -> partner(code, 5)).isInstanceOf(DomainException.class);
    }

    @Test
    void aNegativeQuantityIsRejected() {
        assertThatThrownBy(() -> partner("eva", -1)).isInstanceOf(DomainException.class);
    }

    @Test
    void setCategoryMapKeepsExistingRowsSoFeedCategoryIdsStayTheSame() {
        Partner partner = partner();
        partner.setCategoryMap(List.of(category(5, null, "100", "", 10, null), category(5, "M", "", "", null, null)));
        PartnerCategoryMap wholeCategory = partner.getCategoryMap().get(0);

        partner.setCategoryMap(List.of(category(5, " ", "200", "Сорочки", 15, 30), category(6, null, "", "", null, null)));

        assertThat(partner.getCategoryMap()).hasSize(2);
        assertThat(partner.getCategoryMap().get(0)).isSameAs(wholeCategory); // an empty size = all sizes, the same row
        assertThat(wholeCategory.getExternalId()).isEqualTo("200");
        assertThat(wholeCategory.getExternalName()).isEqualTo("Сорочки");
        assertThat(wholeCategory.getPercentSupply()).isEqualByComparingTo("15");
        assertThat(wholeCategory.getPercentSale()).isEqualByComparingTo("30");
        assertThat(partner.getCategoryMap().get(1).getCategoryId()).isEqualTo(6);
    }

    @Test
    void setCategoryMapRejectsDuplicatesAndBadPercents() {
        Partner partner = partner();

        assertThatThrownBy(() -> partner.setCategoryMap(List.of(category(5, "M", "", "", null, null), category(5, "M ", "", "", null, null))))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> partner.setCategoryMap(List.of(category(5, null, "", "", -100, null)))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> partner.setCategoryMap(List.of(category(0, null, "", "", null, null)))).isInstanceOf(DomainException.class);
    }

    @Test
    void setProductMapRejectsAZeroPrice() {
        assertThatThrownBy(() -> partner().setProductMap(List.of(new ProductMapping(10, null, "", "", BigDecimal.ZERO, null))))
                .isInstanceOf(DomainException.class);
    }
}
