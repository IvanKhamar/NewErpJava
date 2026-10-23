package ua.newerp.domain.partners;

import java.math.BigDecimal;
import java.util.List;

/**
 * An enabled shop product with everything the feed needs.
 *
 * @param categoryIds all categories of the product
 * @param images      photo paths relative to the shop's image/ folder: the main one, then the extra ones in order
 * @param options     the product's option values by option_value_id ascending, as the old feed went through them
 */
public record FeedProduct(int productId, String model, String sku, BigDecimal price, String name, String description, List<Integer> categoryIds,
        List<String> images, List<FeedProductOption> options) {
}
