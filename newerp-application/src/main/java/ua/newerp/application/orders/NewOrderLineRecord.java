package ua.newerp.application.orders;

import ua.newerp.domain.orders.OrderDraftLine;

/** A checked order line with the product's name and model, and the chosen option (null when the product has none). */
public record NewOrderLineRecord(OrderDraftLine line, String name, String model, ProductOptionValueDto option) {
}
