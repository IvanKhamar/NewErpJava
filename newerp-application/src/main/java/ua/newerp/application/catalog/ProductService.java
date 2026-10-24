package ua.newerp.application.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.PagedResult;
import ua.newerp.domain.catalog.ProductChange;
import ua.newerp.domain.common.DomainException;

/**
 * Shop products as the old site's "Каталог → Товари": the ERP changes price, on/off, labels and categories. New products,
 * descriptions, photos and SEO URLs stay in the OpenCart admin; quantities and sort order belong to production and
 * "Порядок товарів".
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

    /**
     * Changes price, on/off, labels and categories. OpenCart's tables are MyISAM, without transactions, so there is no rollback:
     * the guarded product UPDATE goes first and the label and category links change only after it went through.
     */
    @Transactional
    public ProductCardDto update(int productId, UpdateProductRequest request) {
        ProductCardDto current = get(productId);
        if (request.dateModified() == null) {
            throw new DomainException("Не вказано dateModified: з якою версією товару ви працювали.");
        }
        Set<Integer> knownLabels = ids(store.labels());
        // A category switched off later may stay on the product, but cannot be newly chosen.
        Set<Integer> knownCategories = ids(store.categories());
        knownCategories.addAll(ids(current.categories()));
        ProductChange change = ProductChange.of(request.price(), request.enabled(), request.labelIds(), request.categoryIds(), knownLabels,
                knownCategories);

        if (!store.update(productId, request.dateModified(), change.price(), change.enabled())) {
            throw new OptimisticLockingFailureException("Товар №" + productId
                    + " змінили в OpenCart або в іншому вікні після того, як ви його відкрили. Оновіть сторінку й повторіть зміни.");
        }
        if (change.labelIds() != null) {
            store.replaceLabels(productId, change.labelIds());
        }
        if (change.categoryIds() != null) {
            store.replaceCategories(productId, change.categoryIds());
        }
        return get(productId);
    }

    public List<LookupItem> labels() {
        return store.labels();
    }

    public List<LookupItem> categories() {
        return store.categories();
    }

    private static Set<Integer> ids(List<LookupItem> items) {
        return items.stream().map(LookupItem::id).collect(Collectors.toCollection(HashSet::new));
    }
}
