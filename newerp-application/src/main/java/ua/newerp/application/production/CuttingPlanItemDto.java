package ua.newerp.application.production;

import java.math.BigDecimal;
import java.util.List;

/** A consumable the plan's cut takes. shortage: how much is missing on stock (0 when enough); models: who needs it. */
public record CuttingPlanItemDto(int itemId, String name, String unit, BigDecimal need, BigDecimal stock, BigDecimal shortage,
        List<String> models) {
}
