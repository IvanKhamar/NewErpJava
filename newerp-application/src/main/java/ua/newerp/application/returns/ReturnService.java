package ua.newerp.application.returns;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.returns.ProductReturn;
import ua.newerp.domain.returns.ReturnableLine;

/** Product returns (dd_return), as "Продажі → Повернення" of the old site, but tied to the order's lines. */
@Service
@Transactional
public class ReturnService {

    private final ReturnStore store;
    private final Clock clock;

    public ReturnService(ReturnStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResult<ReturnListItem> list(ReturnFilter filter) {
        Paging paging = Paging.normalize(filter.page(), filter.pageSize() == null ? ReturnFilter.DEFAULT_PAGE_SIZE : filter.pageSize());
        String search = filter.search() == null || filter.search().isBlank() ? null : filter.search().strip();
        return store.list(new ReturnFilter(filter.returnId(), filter.orderId(), filter.statusId(), search, paging.page(), paging.pageSize()),
                paging);
    }

    @Transactional(readOnly = true)
    public ReturnDetails get(int returnId) {
        return store.get(returnId).orElseThrow(() -> new NotFoundException("Повернення " + returnId + " не знайдено."));
    }

    @Transactional(readOnly = true)
    public ReturnLookupsDto lookups() {
        return store.lookups();
    }

    public int create(CreateReturnRequest request) {
        ReturnSourceOrder order = store.getSourceOrder(request.orderId())
                .orElseThrow(() -> new DomainException("Замовлення " + request.orderId() + " не знайдено."));
        ReturnLookupsDto lookups = store.lookups();
        requireKnown(lookups.statuses(), request.statusId(), "Оберіть статус повернення.");
        if (request.reasonId() > 0) {
            requireKnown(lookups.reasons(), request.reasonId(), "Такої причини повернення немає.");
        }
        if (request.actionId() != 0) {
            requireKnown(lookups.actions(), request.actionId(), "Такої дії немає.");
        }

        ReturnableLine line = ProductReturn.validate(order.firstname(), order.lastname(), order.telephone(), order.email(), order.lines(),
                request.productId(), request.quantity(), request.reasonId(), request.comment(),
                order.returned().getOrDefault(request.productId(), 0));
        return store.create(new NewReturnRecord(order, line, request, now()));
    }

    public void addHistory(int returnId, AddReturnHistoryRequest request) {
        ReturnLookupsDto lookups = store.lookups();
        requireKnown(lookups.statuses(), request.statusId(), "Оберіть статус повернення.");
        if (request.actionId() != null && request.actionId() != 0) {
            requireKnown(lookups.actions(), request.actionId(), "Такої дії немає.");
        }
        String comment = request.comment() == null ? "" : request.comment().strip();
        if (comment.length() > ProductReturn.MAX_COMMENT_LENGTH) {
            throw new DomainException("Коментар довший за " + ProductReturn.MAX_COMMENT_LENGTH + " символів.");
        }

        if (!store.addHistory(returnId, request.statusId(), comment, request.actionId(), now())) {
            throw new NotFoundException("Повернення " + returnId + " не знайдено.");
        }
    }

    private static void requireKnown(List<ReturnLookupItem> items, int id, String message) {
        if (items.stream().noneMatch(i -> i.id() == id)) {
            throw new DomainException(message);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
