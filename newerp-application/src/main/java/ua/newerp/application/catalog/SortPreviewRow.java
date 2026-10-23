package ua.newerp.application.catalog;

/** A preview row: where the product is now (among the group's products) and where it will be. */
public record SortPreviewRow(int productId, String name, String image, int sales, boolean pinned, int currentPosition, int newPosition) {
}
