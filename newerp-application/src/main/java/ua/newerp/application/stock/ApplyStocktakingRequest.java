package ua.newerp.application.stock;

import java.util.List;

/** counts: the counted quantities (not differences), so a movement between counting and applying is not lost. */
public record ApplyStocktakingRequest(List<StocktakingCountRequest> counts, String comment) {

    public ApplyStocktakingRequest {
        counts = counts == null ? List.of() : counts;
    }
}
