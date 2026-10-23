package ua.newerp.application.stock;

import java.util.List;

/**
 * counts: the counted quantities (not differences), so a movement between counting and applying is not lost.
 * warehouseId: the warehouse that was counted; null is the main stock (all of it while there are no warehouses).
 */
public record ApplyStocktakingRequest(List<StocktakingCountRequest> counts, String comment, Integer warehouseId) {

    public ApplyStocktakingRequest {
        counts = counts == null ? List.of() : counts;
    }

    public ApplyStocktakingRequest(List<StocktakingCountRequest> counts, String comment) {
        this(counts, comment, null);
    }
}
