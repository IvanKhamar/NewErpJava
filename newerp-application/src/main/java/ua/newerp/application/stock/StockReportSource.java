package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Movement totals from dd_stock_log and dd_stock_production_log, summed in the database. */
public interface StockReportSource {

    /**
     * One consumable since a moment. used and received leave stocktaking out; corrected is the stocktaking net
     * (minus means less was counted than recorded).
     */
    record ItemFlow(int itemId, BigDecimal used, BigDecimal received, BigDecimal corrected) {
    }

    /**
     * One size of a model since a moment. sold: taken by orders minus what cancelled orders gave back; shipped: sewn pieces
     * that left the stock not through an order; corrected: the stocktaking net of cut and sewn together.
     */
    record SizeFlow(int productionId, String modelName, int sizeId, String sizeName, BigDecimal cut, BigDecimal sewn,
            BigDecimal sold, BigDecimal shipped, BigDecimal corrected) {
    }

    List<ItemFlow> itemFlows(LocalDateTime since);

    /** In the order of the models and of their sizes. */
    List<SizeFlow> sizeFlows(LocalDateTime since);
}
