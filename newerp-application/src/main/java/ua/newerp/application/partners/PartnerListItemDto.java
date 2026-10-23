package ua.newerp.application.partners;

public record PartnerListItemDto(int id, String code, String name, String vendorName, boolean feedEnabled, boolean isActive, int categories,
        int products) {
}
