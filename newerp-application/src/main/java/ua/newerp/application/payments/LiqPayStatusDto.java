package ua.newerp.application.payments;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** @param state the LiqPayState ordinal: a number, like the .NET enum in JSON */
public record LiqPayStatusDto(int state, String label, BigDecimal amount, LocalDateTime paidAt, boolean isTest) {
}
