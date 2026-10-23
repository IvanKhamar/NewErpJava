package ua.newerp.application.partners;

import java.math.BigDecimal;

/** @param id the row's id, which is also the category id in the feed */
public record PartnerCategoryMapDto(int id, int categoryId, String categoryName, String sizeName, String externalId, String externalName,
        BigDecimal percentSupply, BigDecimal percentSale) {
}
