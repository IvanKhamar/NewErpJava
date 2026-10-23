package ua.newerp.application.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.domain.catalog.CrossSellItem;

/** The checkout cross-sell panel of the old site (dd_crosssell_item). */
public interface CrossSellStore {

    List<CrossSellItemDto> list();

    Optional<CrossSellItemDto> find(int id);

    /** The line for this product or category, if there is one. */
    Optional<Integer> findId(String type, int targetId);

    /** Whether the product or category exists. */
    boolean targetExists(String type, int targetId);

    int add(CrossSellItem item);

    void setDiscount(int id, BigDecimal discount);

    void delete(int id);

    /** Enabled categories by name, for choosing one. */
    List<LookupItem> categories();
}
