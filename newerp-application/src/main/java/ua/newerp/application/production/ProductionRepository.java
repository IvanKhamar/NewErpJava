package ua.newerp.application.production;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ua.newerp.domain.production.ComponentStock;
import ua.newerp.domain.production.ProductionComponent;
import ua.newerp.domain.production.ProductionLogEntry;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;

public interface ProductionRepository {

    /** Active models with cut and sewn totals. */
    List<ProductionModelSummaryDto> list(ProductionModelFilter filter);

    Optional<ProductionModelDto> getDto(int productionId);

    /**
     * Model with sizes, links and sewing BOM, for changes. Locked until the transaction ends, so two operations on
     * the same model run one after another and neither loses the other's stock change.
     */
    Optional<ProductionModel> findModel(int productionId);

    /** Component with its cutting BOM, for changes. */
    Optional<ProductionComponent> findComponent(int componentId);

    List<Integer> componentIds(int productionId);

    /** Those of {@code itemIds} that are not among the active consumables. */
    List<Integer> missingStockItemIds(Collection<Integer> itemIds);

    /** Whether cutting BOMs or non-zero component stock refer to these sizes. */
    boolean sizesInUse(Collection<Integer> sizeIds);

    List<ComponentStock> findComponentStock(int componentId);

    /** Components of the model with cutting BOMs, for operations. */
    List<ProductionComponent> findComponents(int productionId);

    /** Existing component stock rows (a row may not exist yet if the component was never cut). */
    List<ComponentStock> findComponentStocks(Collection<Integer> componentIds);

    /** Consumables for a stock change, locked like in the stock module. */
    List<StockItem> findStockItems(Collection<Integer> itemIds);

    List<ProductionLogDto> listLog(ProductionLogFilter filter);

    /** Order lines with the production model and options (empty when the order has no products). */
    List<OrderSizeLine> getOrderSizeLines(int orderId);

    /** Models with sizes for a stock change, locked like in {@link #findModel}. */
    List<ProductionModel> findModels(Collection<Integer> productionIds);

    /**
     * How many sewn pieces of each size (by size id) this order holds now: written off with the comment
     * "Замовлення #id" minus returned with "Скасування #id". Counted from the shared log, so actions of the old site count too.
     */
    Map<Integer, BigDecimal> getSewnHeldByOrder(int orderId);

    void add(ProductionLogEntry entry);

    void add(StockMovement movement);

    void add(ComponentStock stock);

    void add(ProductionModel model);

    void add(ProductionComponent component);

    void remove(ProductionComponent component);

    void remove(ComponentStock stock);
}
