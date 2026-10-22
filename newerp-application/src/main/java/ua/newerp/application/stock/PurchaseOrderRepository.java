package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ua.newerp.domain.stock.PurchaseOrder;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;

public interface PurchaseOrderRepository {

    List<PurchaseOrderSummaryDto> list(PurchaseOrderFilter filter);

    Optional<PurchaseOrderDto> getDto(int id);

    /** The order with its lines, for changing. */
    Optional<PurchaseOrder> find(int id);

    /** How much of each consumable is ordered and not received yet (drafts and sent orders). */
    Map<Integer, BigDecimal> onOrder();

    /** Active consumables for changing their quantity (receiving). */
    List<StockItem> findItems(Collection<Integer> itemIds);

    void add(PurchaseOrder order);

    void add(StockMovement movement);
}
