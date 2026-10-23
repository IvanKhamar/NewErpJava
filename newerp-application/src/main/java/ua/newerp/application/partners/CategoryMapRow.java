package ua.newerp.application.partners;

import java.math.BigDecimal;

/**
 * @param sizeName      empty: the whole category
 * @param percentSupply markup on the size's price, %; null: no markup
 * @param percentSale   the crossed-out old price as the size's price plus %; null: none
 */
public record CategoryMapRow(int categoryId, String sizeName, String externalId, String externalName, BigDecimal percentSupply,
        BigDecimal percentSale) {
}
