package ua.newerp.application.production;

import java.util.List;

/** Fully replaces the model's links to products. sizeOptions changes only the sizes it lists. */
public record SetProductionLinksRequest(List<Integer> productIds, List<CategoryLinkRequest> categories,
        List<SizeOptionRequest> sizeOptions) {

    public SetProductionLinksRequest {
        productIds = productIds == null ? List.of() : productIds;
        categories = categories == null ? List.of() : categories;
        sizeOptions = sizeOptions == null ? List.of() : sizeOptions;
    }
}
