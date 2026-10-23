package ua.newerp.application.partners;

import java.util.List;

/** A shop product to pick as a single row, with its size prices (or the product's price when it has no sizes). */
public record PartnerProductPickDto(int id, String name, boolean isEnabled, List<ProductSizePriceDto> sizes) {
}
