package ua.newerp.application.reports;

import java.time.LocalDate;

/**
 * Report filters. Without a status all placed orders count except the cancelled ones.
 *
 * @param group Day, Week (default), Month or Year
 */
public record ReportFilter(LocalDate from, LocalDate to, Integer statusId, String group) {
}
