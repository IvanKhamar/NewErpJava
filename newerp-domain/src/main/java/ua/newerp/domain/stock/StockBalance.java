package ua.newerp.domain.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How much of a consumable lies at one warehouse (table erp_stock_location; the warehouses are dd_warehouse).
 * Changed only through {@link StockPlacement}.
 */
public class StockBalance {

    private Integer id;
    private Integer itemId;
    private int warehouseId;
    private BigDecimal quantity = BigDecimal.ZERO;

    protected StockBalance() {
    }

    StockBalance(Integer itemId, int warehouseId) {
        this.itemId = itemId;
        this.warehouseId = warehouseId;
    }

    void add(BigDecimal delta) {
        // the column keeps 3 decimals; the same scale here, so a new balance reads like one loaded from the database
        quantity = quantity.add(delta).setScale(3, RoundingMode.HALF_UP);
    }

    public Integer getId() {
        return id;
    }

    public Integer getItemId() {
        return itemId;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
