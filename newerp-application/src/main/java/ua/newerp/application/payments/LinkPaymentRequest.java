package ua.newerp.application.payments;

/** @param orderId 0: unlink the payment from its order */
public record LinkPaymentRequest(int orderId) {
}
