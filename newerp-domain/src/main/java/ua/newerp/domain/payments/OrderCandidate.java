package ua.newerp.domain.payments;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An order that may be matched with a payment. linkedPaymentId: a payment already linked to it, if any. */
public record OrderCandidate(int orderId, String customer, String telephone, BigDecimal total, BigDecimal prepayment,
        LocalDateTime dateAdded, String status, Integer linkedPaymentId) {
}
