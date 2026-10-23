package ua.newerp.application.production;

import java.util.List;

/** warehouseId: for "sewn" and "ship", the warehouse the pieces arrive at or leave from; null (as in the .NET API) is the main stock. */
public record ProductionOperationRequest(List<SizeQuantity> quantities, String comment, Integer warehouseId) {

    public ProductionOperationRequest {
        quantities = quantities == null ? List.of() : quantities;
    }

    public ProductionOperationRequest(List<SizeQuantity> quantities, String comment) {
        this(quantities, comment, null);
    }
}
