package ua.newerp.application.orders;

import java.util.List;
import java.util.Optional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.PagedResult;

/** Reading orders. The implementation in infrastructure runs SQL against the database. */
public interface OrderQueries {

    PagedResult<OrderListItem> list(OrderFilter filter);

    /** Optional = "maybe a value": Java's way to say "can be missing" instead of returning null. */
    Optional<OrderDetails> get(int orderId);

    List<OrderStatusDto> listStatuses();

    List<LookupItem> listSources();

    /** Who can be made responsible: active users of the old site (as there, anyone from dd_user). */
    List<LookupItem> listManagers();
}
