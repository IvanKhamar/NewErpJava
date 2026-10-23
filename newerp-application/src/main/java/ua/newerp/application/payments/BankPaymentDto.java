package ua.newerp.application.payments;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A payment for the list. orderId = 0: not matched with an order yet. */
public record BankPaymentDto(int id, String appCode, String card, BigDecimal amount, BigDecimal balance, String payer, String description,
        LocalDateTime timestamp, int orderId, String orderCustomer, boolean isVisible) {
}
