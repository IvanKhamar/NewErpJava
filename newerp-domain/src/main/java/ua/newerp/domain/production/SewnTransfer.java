package ua.newerp.domain.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A move of sewn pieces of one size between warehouses (table erp_production_transfer). A null warehouse is the main stock.
 * The size's sewn stock does not change, so a move is not in dd_stock_production_log.
 */
public class SewnTransfer {

    private Integer id;
    private Integer productionId;
    private Integer sizeId;
    private Integer fromWarehouseId;
    private Integer toWarehouseId;
    private BigDecimal quantity = BigDecimal.ZERO;
    private String comment = "";
    private int userId;
    private String username = "";
    private LocalDateTime createdAt;

    protected SewnTransfer() {
    }

    SewnTransfer(Integer productionId, Integer sizeId, Integer fromWarehouseId, Integer toWarehouseId, BigDecimal quantity,
            String comment, int userId, String username, LocalDateTime createdAt) {
        this.productionId = productionId;
        this.sizeId = sizeId;
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

    public Integer getProductionId() {
        return productionId;
    }

    public Integer getSizeId() {
        return sizeId;
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
