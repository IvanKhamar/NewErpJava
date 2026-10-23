package ua.newerp.application.payments;

/**
 * LiqPay (API version 3). The keys (public and private) are known only to the implementation; without them a stub is registered
 * that builds a test link and requests nothing.
 */
public interface LiqPayGateway {

    boolean isTest();

    String checkoutUrl(LiqPayCheckout checkout);

    LiqPayStatusReply status(String orderId);
}
