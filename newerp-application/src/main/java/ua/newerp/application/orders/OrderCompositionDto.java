package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.util.List;

/** An order's composition for editing: products and sums (shipping, discount, prepayment) from the OpenCart totals. */
public record OrderCompositionDto(int orderId, int statusId, List<OrderCompositionLine> lines, BigDecimal shippingCost, BigDecimal discount,
        BigDecimal prepayment, BigDecimal total) {
}
