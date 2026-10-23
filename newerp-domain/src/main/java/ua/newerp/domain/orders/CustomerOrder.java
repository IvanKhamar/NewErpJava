package ua.newerp.domain.orders;

/** One of a customer's previous orders, for the risk check. */
public record CustomerOrder(int orderId, int statusId) {
}
