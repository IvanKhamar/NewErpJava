package ua.newerp.domain.catalog;

/** Whether a size can be bought on the storefront. Sent as a number (ordinal), like the .NET enum. */
public enum SizeAvailability {
    IN_STOCK,
    /** No stock, but there is a pre-order quota: the storefront sells ahead. */
    PRE_ORDER,
    SOLD_OUT
}
