package ua.newerp.domain.production;

import java.math.BigDecimal;

/** Stock of cut components of one size (dd_stock_production_component_stock). */
public class ComponentStock {

    private int componentId;
    private int sizeId;
    private BigDecimal stock = BigDecimal.ZERO;

    protected ComponentStock() {
    }

    public ComponentStock(int componentId, int sizeId) {
        this.componentId = componentId;
        this.sizeId = sizeId;
    }

    /** {@code quantity} more components were cut. */
    public void add(BigDecimal quantity) {
        stock = stock.add(quantity);
    }

    /**
     * Components went to sewing. As with consumables: if there is not enough, it takes what there is.
     *
     * @return how much was actually taken
     */
    public BigDecimal takeUpTo(BigDecimal wanted) {
        BigDecimal taken = wanted.min(stock.max(BigDecimal.ZERO));
        stock = stock.subtract(taken);
        return taken;
    }

    public int getComponentId() {
        return componentId;
    }

    public int getSizeId() {
        return sizeId;
    }

    public BigDecimal getStock() {
        return stock;
    }
}
