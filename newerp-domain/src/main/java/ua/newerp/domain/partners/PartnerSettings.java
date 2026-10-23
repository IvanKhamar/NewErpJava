package ua.newerp.domain.partners;

/** A partner's settings that a manager sets (everything except the category and product mappings). */
public record PartnerSettings(String code, String name, boolean isActive, String feedUrl, boolean feedEnabled, String vendorName,
        String notes, int defaultQuantity) {
}
