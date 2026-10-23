package ua.newerp.domain.customers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;

import ua.newerp.domain.orders.CustomerRisk;

/**
 * A customer's totals by their orders. The old site did not show orders in the customer card at all,
 * so a manager looked up how much a customer bought by searching the order list.
 */
public record CustomerSummary(int orderCount, int cancelledCount, BigDecimal spent, LocalDateTime firstOrder, LocalDateTime lastOrder) {

    /** Cancelled orders ("Відмінено") are not in the spent sum but are counted apart. */
    public static CustomerSummary from(Collection<CustomerOrder> orders) {
        int cancelled = (int) orders.stream().filter(o -> o.statusId() == CustomerRisk.CANCELLED_STATUS_ID).count();
        BigDecimal spent = orders.stream().filter(o -> o.statusId() != CustomerRisk.CANCELLED_STATUS_ID)
                .map(CustomerOrder::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CustomerSummary(orders.size(), cancelled, spent,
                orders.stream().map(CustomerOrder::dateAdded).min(Comparator.naturalOrder()).orElse(null),
                orders.stream().map(CustomerOrder::dateAdded).max(Comparator.naturalOrder()).orElse(null));
    }
}
