package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.util.List;

/** The new composition fully replaces the old one (like editing an order on the old site). */
public record UpdateOrderCompositionRequest(List<NewOrderLineRequest> lines, BigDecimal shippingCost, BigDecimal discount,
        BigDecimal prepayment) {
}
