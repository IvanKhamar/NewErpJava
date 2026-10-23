package ua.newerp.application.reports;

import java.time.LocalDateTime;
import java.util.List;

import ua.newerp.domain.reports.SaleFact;

public interface ReportStore {

    /** Orders dated in [from; toExclusive): with statusId or, without it, any placed one except excludedStatusId. */
    List<SaleFact> sales(LocalDateTime from, LocalDateTime toExclusive, Integer statusId, int excludedStatusId);

    /** Products by quantity sold, with the same conditions on the orders. */
    List<ProductSalesRow> products(LocalDateTime from, LocalDateTime toExclusive, Integer statusId, int excludedStatusId, int limit);

    int newCustomers(LocalDateTime from, LocalDateTime toExclusive);
}
