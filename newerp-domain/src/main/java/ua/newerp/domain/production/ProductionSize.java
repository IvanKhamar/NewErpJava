package ua.newerp.domain.production;

import java.math.BigDecimal;

/** Size of a model with its own cut and sewn stock (dd_stock_production_sizes). */
public class ProductionSize {

    private Integer id;
    private Integer productionId;
    private String name = "";
    private BigDecimal percent = BigDecimal.ZERO;
    private BigDecimal cutStock = BigDecimal.ZERO;
    private BigDecimal sewnStock = BigDecimal.ZERO;
    private int sortOrder;
    private Integer optionValueId;

    protected ProductionSize() {
    }

    ProductionSize(String name, BigDecimal percent, int sortOrder) {
        this.name = name;
        this.percent = percent;
        this.sortOrder = sortOrder;
    }

    // Setters without "public": only ProductionModel (same package) changes a size.
    void setPercent(BigDecimal percent) {
        this.percent = percent;
    }

    void setCutStock(BigDecimal cutStock) {
        this.cutStock = cutStock;
    }

    void setSewnStock(BigDecimal sewnStock) {
        this.sewnStock = sewnStock;
    }

    void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    void setOptionValueId(Integer optionValueId) {
        this.optionValueId = optionValueId;
    }

    public Integer getId() {
        return id;
    }

    public Integer getProductionId() {
        return productionId;
    }

    public String getName() {
        return name;
    }

    /** Share of the size in a batch, %. */
    public BigDecimal getPercent() {
        return percent;
    }

    /** Cut, but not sewn yet. */
    public BigDecimal getCutStock() {
        return cutStock;
    }

    /** Sewn and ready to sell. */
    public BigDecimal getSewnStock() {
        return sewnStock;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /** OpenCart option value (size in the product card) this size is linked to. */
    public Integer getOptionValueId() {
        return optionValueId;
    }
}
