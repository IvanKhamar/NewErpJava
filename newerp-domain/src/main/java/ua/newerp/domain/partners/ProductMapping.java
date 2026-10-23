package ua.newerp.domain.partners;

import java.math.BigDecimal;

/** A row of a single product in the feed: the product (if needed, only one size) with the partner's code, name and prices. */
public record ProductMapping(int productId, String sizeName, String externalId, String externalName, BigDecimal priceSupply,
        BigDecimal priceSale) {
}
