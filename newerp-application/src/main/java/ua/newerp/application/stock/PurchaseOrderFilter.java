package ua.newerp.application.stock;

/** status: draft, ordered, received, cancelled. */
public record PurchaseOrderFilter(String status) {
}
