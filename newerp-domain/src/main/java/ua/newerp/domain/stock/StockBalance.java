package ua.newerp.domain.stock;

import java.math.BigDecimal;

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
        quantity = quantity.add(delta);
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
