package ua.newerp.application.production;

import java.util.List;

import ua.newerp.domain.production.ProductionModel;

/** type: "clothing" (own sewing) or "ready_made" (ready goods from a supplier). */
public record SaveProductionModelRequest(String name, List<SizeRequest> sizes, String type, Integer minTotal, int sortOrder) {

    public SaveProductionModelRequest {
        sizes = sizes == null ? List.of() : sizes;
        type = type == null ? ProductionModel.CLOTHING : type;
        minTotal = minTotal == null ? 50 : minTotal;
    }
}
