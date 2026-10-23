package ua.newerp.application.payments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** A transaction from the bank statement, as the bank returned it. */
public record BankTransaction(String id, String account, LocalDate date, LocalTime time, BigDecimal amount, BigDecimal balance,
        boolean isIncoming, String purpose, String payer, String payerAccount, String payerBankCode, String payerTaxCode) {
}
