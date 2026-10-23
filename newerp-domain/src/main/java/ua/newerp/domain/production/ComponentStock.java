package ua.newerp.domain.production;

import java.math.BigDecimal;

import ua.newerp.domain.common.DomainException;

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

    /**
     * Stocktaking: the stock becomes what was counted. The old module keeps no log of component stock, so neither does this.
     *
     * @return whether the stock changed
     */
    public boolean recount(BigDecimal actual) {
        if (actual == null || actual.signum() < 0 || actual.stripTrailingZeros().scale() > 3) {
            throw new DomainException("Кількість деталей має бути від 0, не більше 3 знаків після коми.");
        }
        boolean changed = actual.compareTo(stock) != 0;
        stock = actual;
        return changed;
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
