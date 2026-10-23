package ua.newerp.application.partners;

import java.util.List;

/** Missing fields get the same defaults as in .NET: active, vendor "Vidro", 5 pieces in the feed. */
public record SavePartnerRequest(String code, String name, Boolean isActive, String feedUrl, boolean feedEnabled, String vendorName,
        String notes, Integer defaultQuantity, List<CategoryMapRow> categoryMap, List<ProductMapRow> productMap) {

    public SavePartnerRequest {
        isActive = isActive == null || isActive;
        feedUrl = feedUrl == null ? "" : feedUrl;
        vendorName = vendorName == null ? "Vidro" : vendorName;
        notes = notes == null ? "" : notes;
        defaultQuantity = defaultQuantity == null ? 5 : defaultQuantity;
        categoryMap = categoryMap == null ? List.of() : categoryMap;
        productMap = productMap == null ? List.of() : productMap;
    }
}
