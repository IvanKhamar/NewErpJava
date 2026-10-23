package ua.newerp.application.production;

import java.util.Collection;

/**
 * Passes the sewn stock to the shop's storefront (OpenCart), like pushStockToOc of the old StockTrack module:
 * the quantity of each linked product size = the sewn stock of the model's size, the product's quantity = their sum.
 */
public interface StorefrontStock {

    /** @param productionIds models whose stock to pass; null: all active ones */
    StorefrontSyncResult push(Collection<Integer> productionIds);
}
