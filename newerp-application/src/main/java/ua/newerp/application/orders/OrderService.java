package ua.newerp.application.orders;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.orders.OrderStatusChange;

/** Order change use cases: status and responsible manager. */
@Service
@Transactional
public class OrderService {

    private final OrderStore store;
    private final Clock clock;
    private final List<OrderStatusHandler> handlers;

    /** A List parameter gets every bean of that type, like IEnumerable&lt;IOrderStatusHandler&gt; in .NET. */
    public OrderService(OrderStore store, Clock clock, List<OrderStatusHandler> handlers) {
        this.store = store;
        this.clock = clock;
        this.handlers = handlers;
    }

    public ChangeOrderStatusResult changeStatus(int orderId, ChangeOrderStatusRequest request, int userId) {
        int currentStatusId = requireStatusId(orderId);
        if (!store.statusExists(request.statusId())) {
            throw new DomainException("Статусу " + request.statusId() + " не існує.");
        }
        requireActiveUser(userId);

        OrderStatusChange change = OrderStatusChange.create(orderId, currentStatusId, request.statusId(), userId, now());

        // Handlers (for example, the sewn stock write-off) run before the status is saved: if one fails, the status
        // stays old and the action can simply be repeated. The handlers themselves skip a repeated write-off.
        var warnings = new ArrayList<String>();
        for (OrderStatusHandler handler : handlers) {
            warnings.addAll(handler.onStatusChanging(change));
        }

        store.saveStatusChange(change);
        return new ChangeOrderStatusResult(warnings);
    }

    /**
     * Changes an order's lines so that other modules (sewn stock) take it into account. Like the old site's editOrder:
     * first the order as if leaves its status (the handlers give back what it held), then the lines change, and the
     * order as if enters the status again (the handlers take by the new lines). The status and its log do not change.
     */
    public List<String> reapplyStatus(int orderId, int statusId, Runnable change, int userId) {
        if (statusId <= 0) {
            change.run();
            return List.of();
        }

        LocalDateTime now = now();
        for (OrderStatusHandler handler : handlers) {
            handler.onStatusChanging(new OrderStatusChange(orderId, statusId, 0, userId, now));
        }
        change.run();
        var warnings = new ArrayList<String>();
        for (OrderStatusHandler handler : handlers) {
            warnings.addAll(handler.onStatusChanging(new OrderStatusChange(orderId, 0, statusId, userId, now)));
        }
        return warnings;
    }

    public void assignManager(int orderId, AssignManagerRequest request, int userId) {
        requireStatusId(orderId);
        requireActiveUser(request.managerId());
        requireActiveUser(userId);

        store.saveManager(orderId, request.managerId(), userId, now());
    }

    private int requireStatusId(int orderId) {
        return store.getStatusId(orderId).orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено."));
    }

    private void requireActiveUser(int userId) {
        if (!store.isActiveUser(userId)) {
            throw new DomainException("Користувача " + userId + " не знайдено або його вимкнено.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
