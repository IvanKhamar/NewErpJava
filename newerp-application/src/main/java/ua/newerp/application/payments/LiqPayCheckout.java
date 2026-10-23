package ua.newerp.application.payments;

import java.math.BigDecimal;

/** What we send to LiqPay for a payment: the site's order number, so the old site's existing callback recognises it. */
public record LiqPayCheckout(String orderId, BigDecimal amount, String currency, String description) {
}
