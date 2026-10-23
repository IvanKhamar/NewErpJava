package ua.newerp.application.partners;

import java.time.LocalDateTime;
import java.util.List;

public record PartnerDto(int id, String code, String name, boolean isActive, String feedUrl, boolean feedEnabled, String vendorName,
        String notes, int defaultQuantity, LocalDateTime dateModified, List<PartnerCategoryMapDto> categoryMap,
        List<PartnerProductMapDto> productMap) {
}
