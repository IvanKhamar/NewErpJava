package ua.newerp.application.returns;

import java.time.LocalDateTime;
import java.util.Optional;

import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;

public interface ReturnStore {

    PagedResult<ReturnListItem> list(ReturnFilter filter, Paging paging);

    Optional<ReturnDetails> get(int returnId);

    ReturnLookupsDto lookups();

    /** The order with its products and how many of each are already returned; empty when there is no such order. */
    Optional<ReturnSourceOrder> getSourceOrder(int orderId);

    /** Writes the return and the first log entry. Returns the id. */
    int create(NewReturnRecord record);

    /** Like the old site's addReturnHistory: a new status (and action, if given) and a log entry. */
    boolean addHistory(int returnId, int statusId, String comment, Integer actionId, LocalDateTime now);
}
