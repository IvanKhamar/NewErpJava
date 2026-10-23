package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.util.List;

/** warnings: for example, not enough sewn pieces for the new lines. */
public record UpdateOrderCompositionResult(BigDecimal total, List<String> warnings) {
}
