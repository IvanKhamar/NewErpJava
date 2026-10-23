package ua.newerp.domain.catalog;

/** A product pinned by hand to a position in a group (positions count from 1). */
public record SortPin(int position, int productId) {
}
