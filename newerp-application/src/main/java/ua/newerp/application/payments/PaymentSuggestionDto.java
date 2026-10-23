package ua.newerp.application.payments;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An order suggested for a payment and what matched in it (reasons: MatchReason flags). */
public record PaymentSuggestionDto(int orderId, String customer, String telephone, BigDecimal total, BigDecimal prepayment,
        LocalDateTime dateAdded, String status, Integer linkedPaymentId, int reasons, int score) {
}
