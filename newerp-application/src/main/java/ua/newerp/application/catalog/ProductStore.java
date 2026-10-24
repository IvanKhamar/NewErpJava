package ua.newerp.application.catalog;

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
}
