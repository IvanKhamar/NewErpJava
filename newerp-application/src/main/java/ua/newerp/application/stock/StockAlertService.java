package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.production.ProductionModelFilter;
import ua.newerp.application.production.ProductionRepository;

/**
 * Low-stock alerts ("сповіщення про мінімальний залишок" of the old CRM): consumables below min_stock and models whose sewn
 * stock is below min_total. Counted on request, so the home page always shows the current state.
 */
@Service
@Transactional(readOnly = true)
public class StockAlertService {

    private final StockRepository stock;
    private final PurchaseOrderRepository purchaseOrders;
    private final ProductionRepository production;

    public StockAlertService(StockRepository stock, PurchaseOrderRepository purchaseOrders, ProductionRepository production) {
        this.stock = stock;
        this.purchaseOrders = purchaseOrders;
        this.production = production;
    }

    /** Items not ordered yet come first, then by how far below the minimum they are (the share of the minimum left). */
    public StockAlertsDto get() {
        Map<Integer, BigDecimal> onOrder = purchaseOrders.onOrder();
        List<StockAlertItemDto> items = stock.listItems(StockItemFilter.BELOW_MINIMUM).stream()
                .map(i -> new StockAlertItemDto(i.id(), i.name(), i.unit(), i.quantity(), i.minQuantity(),
                        onOrder.getOrDefault(i.id(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing((StockAlertItemDto i) -> i.onOrder().signum() > 0)
                        .thenComparing(StockAlertService::shareLeft))
                .toList();
        List<StockAlertModelDto> models = production.list(new ProductionModelFilter(null, true)).stream()
                .map(m -> new StockAlertModelDto(m.id(), m.name(), m.minTotal(), m.totalSewn(), m.deficit()))
                .sorted(Comparator.comparing(StockAlertModelDto::deficit).reversed())
                .toList();
        int notOrdered = (int) items.stream().filter(i -> i.onOrder().signum() == 0).count();
        return new StockAlertsDto(items, notOrdered, models);
    }

    private static BigDecimal shareLeft(StockAlertItemDto item) {
        return item.minQuantity().signum() == 0
                ? BigDecimal.ZERO
                : item.quantity().max(BigDecimal.ZERO).divide(item.minQuantity(), 4, RoundingMode.HALF_UP);
    }
}
