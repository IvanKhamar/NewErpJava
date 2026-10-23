package ua.newerp.application.production;

import java.util.List;

/** Counted quantities (not differences) of a model's sizes and cut components. */
public record ProductionStocktakingRequest(List<SizeCountRequest> sizes, List<ComponentCountRequest> components, String comment) {

    public ProductionStocktakingRequest {
        sizes = sizes == null ? List.of() : sizes;
        components = components == null ? List.of() : components;
    }
}
