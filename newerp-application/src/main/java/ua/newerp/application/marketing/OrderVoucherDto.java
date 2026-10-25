package ua.newerp.application.marketing;

import java.math.BigDecimal;

/** A gift voucher spent in an order: what it paid, the order's new total and what stays on the voucher. */
public record OrderVoucherDto(int orderId, int voucherId, String code, BigDecimal amount, BigDecimal orderTotal, BigDecimal voucherBalance) {
}
