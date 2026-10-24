package ua.newerp.domain.catalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import ua.newerp.domain.common.DomainException;

/**
 * What the ERP may change in a shop product: price, on/off, labels and categories. A null part stays as it is. Quantities,
 * sort order, descriptions and photos are not here: production, "Порядок товарів" and the OpenCart admin own them.
 */
public record ProductChange(BigDecimal price, Boolean enabled, Set<Integer> labelIds, Set<Integer> categoryIds) {

    /**
     * Checks a change against the labels and categories that may be chosen.
     *
     * @param knownLabels     all label ids
     * @param knownCategories enabled categories plus the ones the product is already in (a switched-off one may stay)
     */
    public static ProductChange of(BigDecimal price, Boolean enabled, Collection<Integer> labelIds, Collection<Integer> categoryIds,
            Set<Integer> knownLabels, Set<Integer> knownCategories) {
        if (price != null && (price.signum() < 0 || price.stripTrailingZeros().scale() > 2)) {
            throw new DomainException("Ціна — не менше 0, не більше двох знаків після коми.");
        }
        Set<Integer> labels = labelIds == null ? null : new LinkedHashSet<>(labelIds);
        if (labels != null && !knownLabels.containsAll(labels)) {
            throw new DomainException("Такої мітки немає.");
        }
        Set<Integer> categories = categoryIds == null ? null : new LinkedHashSet<>(categoryIds);
        if (categories != null) {
            // Without a category the product disappears from the shop's menus.
            if (categories.isEmpty()) {
                throw new DomainException("Оберіть хоча б одну категорію.");
            }
            if (!knownCategories.containsAll(categories)) {
                throw new DomainException("Такої категорії немає або вона вимкнена.");
            }
        }
        return new ProductChange(price, enabled, labels, categories);
    }
}
