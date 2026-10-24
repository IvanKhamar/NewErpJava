package ua.newerp.application.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.PagedResult;

/**
 * Shop products as the old site's "Каталог → Товари". New products, descriptions, photos and SEO URLs stay in the OpenCart
 * admin; quantities and sort order belong to production and "Порядок товарів".
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductStore store;

    public ProductService(ProductStore store) {
        this.store = store;
    }

    public PagedResult<ProductListItem> list(ProductFilter filter) {
        return store.list(filter);
    }

    public ProductCardDto get(int productId) {
        return store.find(productId).orElseThrow(() -> new NotFoundException("Товар " + productId + " не знайдено."));
    }

    public List<LookupItem> labels() {
        return store.labels();
    }

    public List<LookupItem> categories() {
        return store.categories();
    }
}
