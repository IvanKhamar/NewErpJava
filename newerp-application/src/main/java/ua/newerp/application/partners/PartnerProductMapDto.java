package ua.newerp.application.partners;

import java.math.BigDecimal;

public record PartnerProductMapDto(int id, int productId, String productName, boolean productEnabled, String sizeName, String externalId,
        String externalName, BigDecimal priceSupply, BigDecimal priceSale) {
}
