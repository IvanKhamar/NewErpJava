package ua.newerp.application.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A PATCH of a shop product: a null field stays as it is. dateModified is the card's dateModified the user saw; if the product
 * changed since (in OpenCart or in another window), the change is refused with 409.
 */
public record UpdateProductRequest(BigDecimal price, Boolean enabled, List<Integer> labelIds, List<Integer> categoryIds,
        LocalDateTime dateModified) {
}
