package ua.newerp.domain.production;

/** OpenCart product linked to a model directly (dd_stock_production_products). */
public class ProductionProductLink {

    private Integer productionId;
    private int productId;

    protected ProductionProductLink() {
    }

    ProductionProductLink(int productId) {
        this.productId = productId;
    }

    public Integer getProductionId() {
        return productionId;
    }

    public int getProductId() {
        return productId;
    }
}
