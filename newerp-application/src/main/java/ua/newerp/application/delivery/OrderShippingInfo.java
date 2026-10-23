package ua.newerp.application.delivery;

import java.math.BigDecimal;

/** Order data the waybill form is filled from. prepayment: a deposit already received before shipping. */
public record OrderShippingInfo(String firstName, String lastName, String telephone, String city, String warehouse, BigDecimal total,
        String paymentCode, BigDecimal prepayment) {
}
