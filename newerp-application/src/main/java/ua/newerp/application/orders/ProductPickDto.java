package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.util.List;

/** A product to add to an order. price is the base price; an option's price, when above 0, replaces it. */
public record ProductPickDto(int id, String name, String model, BigDecimal price, int quantity, List<ProductOptionValueDto> options) {
}
