package ua.newerp.domain.production;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Sewn pieces of one size at one warehouse (table erp_production_location). Changed only through {@link SewnPlacement}. */
public class SewnBalance {

    private Integer id;
    private Integer sizeId;
    private int warehouseId;
    private BigDecimal quantity = BigDecimal.ZERO;

    protected SewnBalance() {
    }

    SewnBalance(Integer sizeId, int warehouseId) {
        this.sizeId = sizeId;
        this.warehouseId = warehouseId;
    }

    void add(BigDecimal delta) {
        // the column keeps 3 decimals; the same scale here, so a new balance reads like one loaded from the database
        quantity = quantity.add(delta).setScale(3, RoundingMode.HALF_UP);
    }

    public Integer getId() {
        return id;
    }

    public Integer getSizeId() {
        return sizeId;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
