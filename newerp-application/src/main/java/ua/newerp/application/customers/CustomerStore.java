package ua.newerp.application.customers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;

public interface CustomerStore {

    PagedResult<CustomerListItem> list(CustomerFilter filter, Paging paging);

    List<CustomerGroupDto> groups();

    Optional<CustomerProfile> get(int customerId);

    /** The customer's orders: by customer_id and by the same phone (phoneKey, the last 9 digits), as guests have no id. */
    List<CustomerOrderItem> orders(int customerId, String phoneKey);

    /** false when there is no such customer. */
    boolean addNote(int customerId, String comment, LocalDateTime now);
}
