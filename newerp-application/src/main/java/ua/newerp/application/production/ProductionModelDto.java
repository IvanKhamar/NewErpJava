package ua.newerp.application.production;

import java.util.List;

public record ProductionModelDto(
        int id,
        String name,
        String type,
        int minTotal,
        int sortOrder,
        List<ProductionSizeDto> sizes,
        List<Integer> productIds,
        List<CategoryLinkDto> categories,
        List<ComponentDto> components,
        List<SewnBomLineDto> sewnBom) {
}
