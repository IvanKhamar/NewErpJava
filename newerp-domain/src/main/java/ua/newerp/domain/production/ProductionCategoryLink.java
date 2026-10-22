package ua.newerp.domain.production;

/**
 * Link through an OpenCart category (dd_stock_production_categories): products of the category
 * whose name contains {@code nameFilter} (or equals it, when {@code matchExact}).
 */
public class ProductionCategoryLink {

    private Integer productionId;
    private int categoryId;
    private String nameFilter = "";
    private boolean matchExact;

    protected ProductionCategoryLink() {
    }

    ProductionCategoryLink(int categoryId, String nameFilter, boolean matchExact) {
        this.categoryId = categoryId;
        this.nameFilter = nameFilter;
        this.matchExact = matchExact;
    }

    void setFilter(String nameFilter, boolean matchExact) {
        this.nameFilter = nameFilter;
        this.matchExact = matchExact;
    }

    public Integer getProductionId() {
        return productionId;
    }

    public int getCategoryId() {
        return categoryId;
    }

    /** Empty means the whole category. */
    public String getNameFilter() {
        return nameFilter;
    }

    public boolean isMatchExact() {
        return matchExact;
    }
}
