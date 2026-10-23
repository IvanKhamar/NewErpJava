package ua.newerp.domain.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A move of a consumable between warehouses (table erp_stock_transfer). A null warehouse is the main stock. The item's total
 * does not change, so a move is not in dd_stock_log.
 */
public class StockTransfer {

    private Integer id;
    private Integer itemId;
    private Integer fromWarehouseId;
    private Integer toWarehouseId;
    private BigDecimal quantity = BigDecimal.ZERO;
    private String comment = "";
    private int userId;
    private String username = "";
    private LocalDateTime createdAt;

    protected StockTransfer() {
    }

    StockTransfer(Integer itemId, Integer fromWarehouseId, Integer toWarehouseId, BigDecimal quantity, String comment,
            int userId, String username, LocalDateTime createdAt) {
        this.itemId = itemId;
        this.fromWarehouseId = fromWarehouseId;
        this.toWarehouseId = toWarehouseId;
        this.quantity = quantity;
        this.comment = comment;
        this.userId = userId;
        this.username = username;
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public Integer getItemId() {
        return itemId;
    }

    public Integer getFromWarehouseId() {
        return fromWarehouseId;
    }

    public Integer getToWarehouseId() {
        return toWarehouseId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getComment() {
        return comment;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
