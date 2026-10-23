package ua.newerp.domain.orders;

import java.math.BigDecimal;

/** A line of a new order: the product, the chosen option (size) and the price the manager named. */
public record OrderDraftLine(int productId, Integer productOptionValueId, int quantity, BigDecimal price) {

    public BigDecimal total() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
