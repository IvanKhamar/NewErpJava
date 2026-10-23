package ua.newerp.application.returns;

/** The customer and the product come from the order; actionId = 0: the action is not decided yet. */
public record CreateReturnRequest(int orderId, int productId, int quantity, int reasonId, int statusId, int actionId, boolean opened,
        String comment) {
}
