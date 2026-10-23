package ua.newerp.application.customers;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;
import ua.newerp.domain.customers.CustomerNote;
import ua.newerp.domain.customers.CustomerOrder;
import ua.newerp.domain.customers.CustomerSummary;
import ua.newerp.domain.orders.CustomerRisk;

/** Customers (dd_customer), like "Покупці → Покупці" of the old site, with their order history and managers' notes. */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerStore store;
    private final Clock clock;

    public CustomerService(CustomerStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public PagedResult<CustomerListItem> list(CustomerFilter filter) {
        Paging paging = Paging.normalize(filter.page(), filter.pageSize() == null ? CustomerFilter.DEFAULT_PAGE_SIZE : filter.pageSize());
        String search = filter.search() == null || filter.search().isBlank() ? null : filter.search().strip();
        return store.list(new CustomerFilter(search, filter.groupId(), filter.status(), paging.page(), paging.pageSize()), paging);
    }

    public List<CustomerGroupDto> groups() {
        return store.groups();
    }

    public CustomerDetails get(int customerId) {
        CustomerProfile c = store.get(customerId).orElseThrow(() -> new NotFoundException("Покупця " + customerId + " не знайдено."));
        List<CustomerOrderItem> orders = store.orders(customerId, CustomerRisk.phoneKey(c.telephone()));
        CustomerSummary summary = CustomerSummary.from(orders.stream()
                .map(o -> new CustomerOrder(o.id(), o.dateAdded(), o.statusId(), o.total())).toList());
        CustomerRisk risk = CustomerRisk.from(orders.stream()
                .map(o -> new ua.newerp.domain.orders.CustomerOrder(o.id(), o.statusId())).toList());
        return new CustomerDetails(c.id(), c.firstname(), c.lastname(), c.email(), c.telephone(), c.groupName(), c.status(), c.newsletter(),
                c.dateAdded(), c.addresses(), orders,
                new CustomerStatsDto(summary.orderCount(), summary.cancelledCount(), summary.spent(), summary.firstOrder(), summary.lastOrder(),
                        levelName(risk), risk.message()),
                c.notes());
    }

    @Transactional
    public void addNote(int customerId, AddCustomerNoteRequest request) {
        String comment = CustomerNote.validate(request.comment());
        if (!store.addNote(customerId, comment, LocalDateTime.now(clock))) {
            throw new NotFoundException("Покупця " + customerId + " не знайдено.");
        }
    }

    // The .NET enum name as text: RED -> "Red".
    private static String levelName(CustomerRisk risk) {
        String name = risk.level().name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
