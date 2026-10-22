package ua.newerp.application.stock;

public record StockItemFilter(Integer categoryId, Boolean belowMinimumOnly) {

    public static final StockItemFilter BELOW_MINIMUM = new StockItemFilter(null, true);

    public boolean onlyBelowMinimum() {
        return Boolean.TRUE.equals(belowMinimumOnly);
    }
}
