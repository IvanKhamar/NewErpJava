package ua.newerp.application.payments;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** LiqPay's raw answer to a status request. */
public record LiqPayStatusReply(String status, String errorCode, BigDecimal amount, LocalDateTime endDate) {
}
