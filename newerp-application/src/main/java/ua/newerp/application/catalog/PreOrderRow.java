package ua.newerp.application.catalog;

import java.time.LocalDate;

/**
 * A product size with its storefront stock, pre-order quota and the date it is expected.
 * availability: SizeAvailability as a number (0 in stock, 1 pre-order, 2 sold out).
 */
public record PreOrderRow(int productOptionValueId, int productId, String productName, String model, String size, int quantity, int quota,
        LocalDate dateAvailable, int availability) {
}
