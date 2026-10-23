package ua.newerp.application.stock;

/** sortOrder: null means 0 for a new category and "keep" when renaming. */
public record CreateStockCategoryRequest(String name, Integer sortOrder) {
}
