package ua.newerp.application.cashbox;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.cashbox.ReceiptSourceLine;

/** What a receipt needs from the order. */
public record OrderForCheque(int orderId, BigDecimal total, BigDecimal prepayment, String paymentCode, String paymentMethod,
        String email, List<ReceiptSourceLine> lines) {

    /** Sum to pay: the total minus the prepayment. */
    public BigDecimal toPay() {
        return total.subtract(prepayment);
    }
}
