package ua.newerp.application.production;

import java.util.List;

public record ProductionOperationRequest(List<SizeQuantity> quantities, String comment) {

    public ProductionOperationRequest {
        quantities = quantities == null ? List.of() : quantities;
    }
}
