package ua.newerp.domain.production;

import java.math.BigDecimal;

/**
 * Cutting BOM row (dd_stock_production_component_bom): how much of a consumable goes into one component.
 * {@code sizeId} = null means all sizes; a row for a specific size overrides it.
 */
public class ComponentBomLine {

    private Integer id;
    private Integer componentId;
    private Integer sizeId;
    private int itemId;
    private BigDecimal qtyPerUnit = BigDecimal.ZERO;

    protected ComponentBomLine() {
    }

    ComponentBomLine(Integer sizeId, int itemId, BigDecimal qtyPerUnit) {
        this.sizeId = sizeId;
        this.itemId = itemId;
        this.qtyPerUnit = qtyPerUnit;
    }

    public Integer getId() {
        return id;
    }

    public Integer getComponentId() {
        return componentId;
    }

    public Integer getSizeId() {
        return sizeId;
    }

    public int getItemId() {
        return itemId;
    }

    public BigDecimal getQtyPerUnit() {
        return qtyPerUnit;
    }
}
