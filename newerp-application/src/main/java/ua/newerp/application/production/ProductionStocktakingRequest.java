package ua.newerp.application.production;

import java.util.List;

/**
 * Counted quantities (not differences) of a model's sizes and cut components. warehouseId: a warehouse where only sewn pieces
 * were counted; null is the main stock, where everything is counted.
 */
public record ProductionStocktakingRequest(List<SizeCountRequest> sizes, List<ComponentCountRequest> components, String comment,
        Integer warehouseId) {

    public ProductionStocktakingRequest {
        sizes = sizes == null ? List.of() : sizes;
        components = components == null ? List.of() : components;
    }

    public ProductionStocktakingRequest(List<SizeCountRequest> sizes, List<ComponentCountRequest> components, String comment) {
        this(sizes, components, comment, null);
    }
}
