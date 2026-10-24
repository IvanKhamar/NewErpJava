package ua.newerp.domain.competitors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

class PriceExtractorTest {

    @Test
    void readsTheUsualShopMarkup() {
        assertThat(PriceExtractor.extract("<meta property=\"product:price:amount\" content=\"1299.00\">", ""))
                .contains(new BigDecimal("1299.00"));
        assertThat(PriceExtractor.extract("<meta content='850' property='product:price:amount'/>", null)).contains(new BigDecimal("850"));
        assertThat(PriceExtractor.extract("<span itemprop=\"price\" content=\"1 450,50\">1 450,50 грн</span>", ""))
                .contains(new BigDecimal("1450.50"));
        assertThat(PriceExtractor.extract("<script type=\"application/ld+json\">{\"@type\":\"Offer\",\"price\": \"999\","
                + "\"priceCurrency\":\"UAH\"}</script>", "")).contains(new BigDecimal("999"));
        assertThat(PriceExtractor.extract("<p>Ціна за домовленістю</p>", "")).isEmpty();
        assertThat(PriceExtractor.extract("", "")).isEmpty();
    }

    @Test
    void theCompetitorsOwnExpressionGoesFirst() {
        String html = "<meta property=\"product:price:amount\" content=\"1000\"><b class=\"new-price\">899 грн</b>";
        assertThat(PriceExtractor.extract(html, "class=\"new-price\">([^<]+)")).contains(new BigDecimal("899"));
        assertThat(PriceExtractor.extract(html, "class=\"none\">([^<]+)")).contains(new BigDecimal("1000"));
    }

    @Test
    void parsesThousandsAndDecimalSeparators() {
        assertThat(PriceExtractor.parse("1 299,00")).contains(new BigDecimal("1299.00"));
        assertThat(PriceExtractor.parse("1,299.00")).contains(new BigDecimal("1299.00"));
        assertThat(PriceExtractor.parse("1.299")).contains(new BigDecimal("1299"));
        assertThat(PriceExtractor.parse("1299.5")).contains(new BigDecimal("1299.5"));
        assertThat(PriceExtractor.parse("2 100 грн.")).contains(new BigDecimal("2100"));
        assertThat(PriceExtractor.parse("0")).isEmpty();
        assertThat(PriceExtractor.parse("немає")).isEmpty();
    }

    @Test
    void checksTheExpression() {
        assertThat(PriceExtractor.requireRegex("  ")).isEmpty();
        assertThat(PriceExtractor.requireRegex(" price\">([0-9 ]+) ")).isEqualTo("price\">([0-9 ]+)");
        assertThatThrownBy(() -> PriceExtractor.requireRegex("price\">[0-9]+")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PriceExtractor.requireRegex("price\">([0-9]+")).isInstanceOf(DomainException.class);
    }
}
