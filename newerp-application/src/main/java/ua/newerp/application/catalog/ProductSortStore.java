package ua.newerp.application.catalog;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import ua.newerp.domain.catalog.SortGroup;
import ua.newerp.domain.catalog.SortPin;

public interface ProductSortStore {

    /** All groups in the recalculation order: themes, categories (colors apart), labels, the cross-sell panel. */
    List<SortGroup> listGroups();

    /** The group's enabled products in the original order: a label from the newest, the panel by its current order, the rest by id. */
    List<Integer> groupProductIds(SortGroup group);

    /** Pieces sold of each product in the period (only counted orders). crossSellOnly: only those added through the panel. */
    Map<Integer, Integer> sales(Collection<Integer> productIds, LocalDateTime from, LocalDateTime to, boolean crossSellOnly);

    /** Name, photo and current order of the products (for the panel its own order, 999999 without one). */
    List<SortProductDto> products(Collection<Integer> productIds, boolean crossSell);

    /** Writes the order 1, 2, 3… to dd_product.sort_order or, for the panel, replaces dd_crosssell_sort. */
    int apply(boolean crossSell, List<Integer> orderedProductIds);

    List<SortPin> pins(SortGroup group);

    List<SortPinDto> listPins();

    /** Pins a product to a position; an earlier pin of this product or this position in the group goes away. */
    void setPin(SortGroup group, int position, int productId, LocalDateTime now);

    boolean removePin(int id);

    /** Saves the current sort_order of all products. */
    int createSnapshot(String note, LocalDateTime now);

    List<SortSnapshotDto> listSnapshots(int limit);

    boolean restoreSnapshot(int id);

    boolean crossSellPanelOnly();

    void setCrossSellPanelOnly(boolean panelOnly);

    /** Enabled products whose name contains term. */
    List<SortProductDto> searchProducts(String term, int limit);
}
