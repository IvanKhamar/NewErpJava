package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.util.List;

/**
 * A line of an order's composition. productOptionValueId: the chosen size or option (null when the product has none);
 * options: the product's sizes in the catalog now, so the size can be changed.
 */
public record OrderCompositionLine(int productId, Integer productOptionValueId, String name, String model, String option, int quantity,
        BigDecimal price, List<ProductOptionValueDto> options) {
}
