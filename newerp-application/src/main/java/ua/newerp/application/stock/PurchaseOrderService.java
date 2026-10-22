package ua.newerp.application.stock;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.stock.PurchaseOrder;
import ua.newerp.domain.stock.PurchaseOrder.LineInput;
import ua.newerp.domain.stock.PurchaseOrder.Receipt;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;

/** Supplier orders: create (by hand or from the "what to reorder" report), send, receive. */
@Service
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository orders;
    private final StockRepository stock;
    private final ReorderService reorder;
    private final Clock clock;

    public PurchaseOrderService(PurchaseOrderRepository orders, StockRepository stock, ReorderService reorder, Clock clock) {
        this.orders = orders;
        this.stock = stock;
        this.reorder = reorder;
        this.clock = clock;
    }

    /** A draft for the supplier with what the "what to reorder" report suggests (quantity and price from the item). */
    public int createFromReorder(int supplierId, int userId) {
        SupplierReorderDto group = reorder.getReport().suppliers().stream()
                .filter(s -> s.supplierId() != null && s.supplierId() == supplierId)
                .findFirst()
                .orElseThrow(() -> new DomainException("У постачальника " + supplierId + " зараз нічого дозамовляти."));

        PurchaseOrder order = new PurchaseOrder(supplierId,
                group.lines().stream().map(l -> new LineInput(l.itemId(), l.suggested(), l.price())).toList(),
                "Зі звіту «що дозамовити»", userId, now());
        orders.add(order);
        return order.getId();
    }

    public int create(CreatePurchaseOrderRequest request, int userId) {
        if (!stock.supplierExists(request.supplierId())) {
            throw new DomainException("Постачальника " + request.supplierId() + " не існує.");
        }
        PurchaseOrder order = new PurchaseOrder(request.supplierId(), validLines(request.lines()), request.comment(), userId, now());
        orders.add(order);
        return order.getId();
    }

    public void setLines(int id, SetPurchaseOrderLinesRequest request) {
        require(id).setLines(validLines(request.lines()));
    }

    public void markOrdered(int id) {
        require(id).markOrdered(now());
    }

    public void cancel(int id) {
        require(id).cancel(now());
    }

    /** Receiving goods: quantities grow, the stock log gets receipts. All in one transaction. */
    public void receive(int id, ReceivePurchaseOrderRequest request, int userId, String username) {
        PurchaseOrder order = require(id);
        Map<Integer, StockItem> items = orders.findItems(order.getLines().stream().map(l -> l.getItemId()).toList())
                .stream().collect(Collectors.toMap(StockItem::getId, Function.identity()));

        List<Receipt> receipts = request.lines() == null ? List.of()
                : request.lines().stream().map(l -> new Receipt(l.lineId(), l.quantity())).toList();
        for (StockMovement movement : order.receive(receipts, items, userId, username, now())) {
            orders.add(movement);
        }
    }

    private List<LineInput> validLines(List<PurchaseOrderLineRequest> lines) {
        List<LineInput> result = lines == null ? List.of()
                : lines.stream().map(l -> new LineInput(l.itemId(), l.quantity(), l.price())).toList();
        List<Integer> ids = result.stream().map(LineInput::itemId).distinct().toList();
        Set<Integer> found = orders.findItems(ids).stream().map(StockItem::getId).collect(Collectors.toSet());
        List<Integer> missing = ids.stream().filter(i -> !found.contains(i)).toList();
        if (!missing.isEmpty()) {
            throw new DomainException("Розхідників " + missing.stream().map(String::valueOf).collect(Collectors.joining(", "))
                    + " немає на складі.");
        }
        return result;
    }

    private PurchaseOrder require(int id) {
        return orders.find(id).orElseThrow(() -> new NotFoundException("Замовлення постачальнику №" + id + " не знайдено."));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
