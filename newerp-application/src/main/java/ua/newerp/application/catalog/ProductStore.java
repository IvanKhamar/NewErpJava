package ua.newerp.application.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.PagedResult;

/** OpenCart's shop products (dd_product and its description, size, label and category tables). */
public interface ProductStore {

    PagedResult<ProductListItem> list(ProductFilter filter);

    Optional<ProductCardDto> find(int productId);

    /** All product labels ("Новинка", "Хіт"…) by their sort order. */
    List<LookupItem> labels();

    /** Enabled categories by name. */
    List<LookupItem> categories();

    /**
     * Sets the price and status (null: as is) and date_modified = now, only if date_modified is still {@code expected}.
     *
     * @return false if the product changed since (or is gone)
     */
    boolean update(int productId, LocalDateTime expected, BigDecimal price, Boolean enabled);

    void replaceLabels(int productId, Collection<Integer> labelIds);

    void replaceCategories(int productId, Collection<Integer> categoryIds);
}
