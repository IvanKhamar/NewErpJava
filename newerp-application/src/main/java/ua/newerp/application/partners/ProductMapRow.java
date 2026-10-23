package ua.newerp.application.partners;

import java.math.BigDecimal;

/** @param priceSupply the price for the partner; null: the shop's price */
public record ProductMapRow(int productId, String sizeName, String externalId, String externalName, BigDecimal priceSupply,
        BigDecimal priceSale) {
}
