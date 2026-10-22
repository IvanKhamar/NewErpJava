package ua.newerp.application.orders;

import java.time.LocalDate;

/** Query string filters of the order list (?statusId=5&amp;page=2). Spring fills a record from them by name. */
public record OrderFilter(
        Integer statusId,
        Integer sourceId,
        Integer managerId,
        LocalDate from,
        LocalDate to,
        String search,
        Integer page,
        Integer pageSize) {
}
