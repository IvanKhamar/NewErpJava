package ua.newerp.application.production;

import java.util.List;

public record CuttingPlanDto(List<CuttingPlanModelDto> models, List<CuttingPlanItemDto> items) {
}
