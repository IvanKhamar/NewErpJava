package ua.newerp.domain.production;

import java.math.BigDecimal;

/**
 * Sewing BOM row (dd_stock_production_sewn_bom): one sewn piece takes either a cut component
 * ({@code componentId}) or a consumable directly ({@code itemId}: threads, labels).
 * Size override works as in {@link ComponentBomLine}.
 */
public class SewnBomLine {

    private Integer id;
    private Integer productionId;
    private Integer sizeId;
    private Integer componentId;
    private Integer itemId;
    private BigDecimal qtyPerUnit = BigDecimal.ZERO;

    protected SewnBomLine() {
    }

    SewnBomLine(Integer sizeId, Integer componentId, Integer itemId, BigDecimal qtyPerUnit) {
        this.sizeId = sizeId;
        this.componentId = componentId;
        this.itemId = itemId;
        this.qtyPerUnit = qtyPerUnit;
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

    public Integer getComponentId() {
        return componentId;
    }

    public Integer getItemId() {
        return itemId;
    }

    public BigDecimal getQtyPerUnit() {
        return qtyPerUnit;
    }
}
