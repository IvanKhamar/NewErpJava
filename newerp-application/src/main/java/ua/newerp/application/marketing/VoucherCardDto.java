package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A gift voucher with its balance. usable: the site and manual orders accept it now (switched on, and paid if it was bought in
 * an order); the balance must still be above 0.
 */
public record VoucherCardDto(int id, int orderId, String code, String fromName, String fromEmail, String toName, String toEmail, int themeId,
        String themeName, String message, BigDecimal amount, BigDecimal balance, boolean active, boolean usable, LocalDateTime createdAt,
        List<HistoryRow> history) {

    /** A spending (negative amount) or a refund (positive) in an order. */
    public record HistoryRow(int orderId, String customer, BigDecimal amount, LocalDateTime date) {
    }
}
