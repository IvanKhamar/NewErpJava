package ua.newerp.application.marketing;

import java.math.BigDecimal;

/** A new or changed gift voucher. An empty code on a new voucher is generated; active null means on. */
public record SaveVoucherRequest(String code, String fromName, String fromEmail, String toName, String toEmail, Integer themeId,
        String message, BigDecimal amount, Boolean active) {
}
