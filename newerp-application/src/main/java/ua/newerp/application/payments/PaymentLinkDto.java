package ua.newerp.application.payments;

import java.math.BigDecimal;

/** isTest: no LiqPay keys are set, the link is a test one and no money will come through it. */
public record PaymentLinkDto(String url, BigDecimal amount, String currency, boolean isTest) {
}
