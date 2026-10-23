package ua.newerp.application.payments;

import java.time.LocalDate;

/**
 * Query string filters of the payment list; page size 50 by default.
 *
 * @param linked true: only matched, false: only unmatched, null: all
 * @param hidden true: show the hidden payments instead of the visible ones
 */
public record BankPaymentFilter(LocalDate from, LocalDate to, Boolean linked, Integer orderId, String search, Boolean hidden,
        Integer page, Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;

    public boolean showHidden() {
        return Boolean.TRUE.equals(hidden);
    }
}
