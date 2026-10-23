package ua.newerp.domain.partners;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.newerp.domain.partners.PartnerTest.category;
import static ua.newerp.domain.partners.PartnerTest.partner;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PartnerFeedBuilderTest {

    static final String STORE = "https://shop.test/";
    static final Map<Integer, String> CATEGORY_NAMES = Map.of(5, "Сорочки", 6, "Сукні", 9, "Аксесуари");

    static FeedProduct product(int id, int category, FeedProductOption... options) {
        return new FeedProduct(id, "T-" + id, "", BigDecimal.valueOf(1000), "Товар " + id, "<p>Льон</p>", List.of(category),
                List.of("catalog/фото 1.jpg"), List.of(options));
    }

    static FeedProductOption size(int optionValueId, String name, int price) {
        return new FeedProductOption(optionValueId, name, BigDecimal.valueOf(price));
    }

    @Test
    void categoryOffersHaveAMarkupAndAnOldPricePerSize() {
        Partner partner = partner();
        partner.setCategoryMap(List.of(category(5, null, "77", "", 10, 25)));

        PartnerFeed feed = PartnerFeedBuilder.build(partner, List.of(product(12, 5, size(46, "S", 900), size(47, "M", 1000))), CATEGORY_NAMES,
                STORE);

        assertThat(feed.offers()).extracting(FeedOffer::id).containsExactly("12_46", "12_47");
        FeedOffer m = feed.offers().get(1);
        assertThat(m.price()).isEqualByComparingTo("1100");
        assertThat(m.priceOld()).isEqualByComparingTo("1250");
        assertThat(List.of(m.name(), m.size(), m.description())).containsExactly("Товар 12 - M", "M", "Льон");
        assertThat(m.barcode()).isEqualTo(PartnerFeedBuilder.barcode("", 12, 1)); // the second size: variant digit 1
        assertThat(m.pictures()).containsExactly("https://shop.test/image/catalog/%D1%84%D0%BE%D1%82%D0%BE%201.jpg");
        assertThat(feed.categories()).containsExactly(new FeedCategory("0", "77", "Сорочки"));
    }

    @Test
    void aSizeRowWinsOverTheWholeCategoryAndUnmappedSizesAreSkipped() {
        Partner partner = partner();
        partner.setCategoryMap(List.of(category(5, "M", "", "", 50, null), category(6, "L", "", "", null, null)));

        PartnerFeed feed = PartnerFeedBuilder.build(partner,
                List.of(product(12, 5, size(46, "S", 900), size(47, "M", 1000)), product(13, 6, size(47, "M", 1000))), CATEGORY_NAMES, STORE);

        assertThat(feed.offers()).hasSize(1);
        FeedOffer offer = feed.offers().get(0);
        assertThat(offer.id()).isEqualTo("12_47");
        assertThat(offer.price()).isEqualByComparingTo("1500");
        assertThat(offer.priceOld()).isNull();
    }

    @Test
    void aProductRowReplacesTheCategoryOfferAndAddsItsOwnCategoryWhenUnmapped() {
        Partner partner = partner();
        partner.setCategoryMap(List.of(category(5, null, "", "", null, null)));
        partner.setProductMap(List.of(
                new ProductMapping(12, "M", "EVA-12", "Сорочка для EVA", BigDecimal.valueOf(1300), BigDecimal.valueOf(1600)),
                new ProductMapping(20, null, "", "", null, null)));
        FeedProduct noSizes = new FeedProduct(20, "T-20", "", BigDecimal.valueOf(450), "Товар 20", "<p>Льон</p>", List.of(9),
                List.of("catalog/фото 1.jpg"), List.of());

        PartnerFeed feed = PartnerFeedBuilder.build(partner, List.of(product(12, 5, size(46, "S", 900), size(47, "M", 1000)), noSizes),
                CATEGORY_NAMES, STORE);

        assertThat(feed.offers()).extracting(FeedOffer::id).containsExactly("12_46", "p12_47", "p20");
        FeedOffer mapped = feed.offers().get(1);
        assertThat(mapped.price()).isEqualByComparingTo("1300");
        assertThat(mapped.priceOld()).isEqualByComparingTo("1600");
        assertThat(List.of(mapped.vendorCode(), mapped.name(), mapped.categoryId())).containsExactly("EVA-12", "Сорочка для EVA", "0");
        FeedOffer single = feed.offers().get(2);
        assertThat(List.of(single.categoryId(), single.name())).containsExactly("p9", "Товар 20");
        assertThat(single.price()).isEqualByComparingTo("450");
        assertThat(feed.categories()).contains(new FeedCategory("p9", null, "Аксесуари"));
    }

    @Test
    void offersWithoutPicturesAreSkipped() {
        Partner partner = partner();
        partner.setCategoryMap(List.of(category(5, null, "", "", null, null)));
        FeedProduct noPhoto = new FeedProduct(12, "T-12", "", BigDecimal.valueOf(1000), "Товар 12", "", List.of(5), List.of(), List.of());

        assertThat(PartnerFeedBuilder.build(partner, List.of(noPhoto), CATEGORY_NAMES, STORE).offers()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"123, 12345, 2, 4820123123426", "'', 102, 0, 4820000010207", "AB1, 7, 11, 4820000000710"})
    void theBarcodeIsTheSameEan13AsOnTheOldSite(String sku, int productId, int variant, String expected) {
        assertThat(PartnerFeedBuilder.barcode(sku, productId, variant)).isEqualTo(expected);
    }

    @Test
    void plainDescriptionDecodesHtmlAndKeepsLineBreaks() {
        assertThat(PartnerFeedBuilder.plainDescription("&lt;p&gt;Льон 100%&lt;/p&gt;&lt;b&gt;Прати&lt;/b&gt; при 30&amp;deg;"))
                .isEqualTo("Льон 100%\nПрати при 30°");
    }
}
