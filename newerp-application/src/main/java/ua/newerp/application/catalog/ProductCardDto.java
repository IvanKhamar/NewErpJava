package ua.newerp.application.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import ua.newerp.application.common.LookupItem;

/**
 * A shop product card. Sizes are read-only here: their quantities come from sewn stock (StorefrontStock). dateModified is
 * dd_product.date_modified, which OpenCart also sets on every save.
 */
public record ProductCardDto(int id, String name, String model, BigDecimal price, boolean enabled, int quantity, String image,
        LocalDateTime dateModified, List<Size> sizes, List<LookupItem> labels, List<LookupItem> categories) {

    public record Size(int optionValueId, String name, int quantity) {
    }
}
