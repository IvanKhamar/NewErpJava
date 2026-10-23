package ua.newerp.application.cashbox;

import java.time.LocalDate;

/** Query string filters of the receipt list; page size 50 by default. */
public record ChequeFilter(Integer cashierId, Integer orderId, LocalDate from, LocalDate to, Boolean fiscalized, Integer page,
        Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;
}
