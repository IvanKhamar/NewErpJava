package ua.newerp.application.catalog;

import java.math.BigDecimal;

/** A shop product in the list. quantity is the storefront stock, filled from sewn stock by the production module. */
public record ProductListItem(int id, String name, String model, BigDecimal price, boolean enabled, int quantity, String image) {
}
