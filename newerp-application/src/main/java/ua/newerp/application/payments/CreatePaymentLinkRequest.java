package ua.newerp.application.payments;

import java.math.BigDecimal;

/** @param amount null: a link for the whole order total */
public record CreatePaymentLinkRequest(BigDecimal amount) {
}
