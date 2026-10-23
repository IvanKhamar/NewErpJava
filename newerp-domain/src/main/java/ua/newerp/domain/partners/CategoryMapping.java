package ua.newerp.domain.partners;

import java.math.BigDecimal;

/** A category mapping row: a shop category (if needed, only one size) → the partner's category. */
public record CategoryMapping(int categoryId, String sizeName, String externalId, String externalName, BigDecimal percentSupply,
        BigDecimal percentSale) {
}
