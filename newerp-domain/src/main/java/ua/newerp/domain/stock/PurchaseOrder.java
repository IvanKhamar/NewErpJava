package ua.newerp.domain.stock;

import static ua.newerp.domain.common.Decimals.isNegative;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;

/**
 * Order to a consumables supplier: draft → ordered → received (or cancelled).
 * It can be received in parts; every receipt is a stock log entry.
 */
public class PurchaseOrder {

    public static final String DRAFT = "draft";
    public static final String ORDERED = "ordered";
    public static final String RECEIVED = "received";
    public static final String CANCELLED = "cancelled";

    /** One line of a new order (C# used a tuple here; in Java a small record does that job). */
    public record LineInput(int itemId, BigDecimal quantity, BigDecimal price) {
    }

    /** How much of one line arrived. */
    public record Receipt(int lineId, BigDecimal quantity) {
    }

    private Integer id;
    private int supplierId;
    private String status = DRAFT;
    private String comment = "";
    private int createdByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime orderedAt;
    private LocalDateTime closedAt;
    private final List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(int supplierId, Collection<LineInput> lines, String comment, int userId, LocalDateTime now) {
        this.supplierId = supplierId;
        this.comment = commentText(comment);
        this.createdByUserId = userId;
        this.createdAt = now;
        setLines(lines);
    }

    /** Whether we still wait for goods (a draft too: it will be sent soon). */
    public boolean isOpen() {
        return DRAFT.equals(status) || ORDERED.equals(status);
    }

    /** Replaces the lines. Only in a draft: an order sent to the supplier is not changed. */
    public void setLines(Collection<LineInput> newLines) {
        requireStatus(DRAFT, "змінювати рядки");
        if (newLines.isEmpty()) {
            throw new DomainException("Замовлення має містити хоча б одну позицію.");
        }
        if (newLines.stream().anyMatch(l -> l.quantity().signum() <= 0)) {
            throw new DomainException("Кількість у замовленні має бути більшою за нуль.");
        }
        if (newLines.stream().anyMatch(l -> isNegative(l.price()))) {
            throw new DomainException("Ціна не може бути від'ємною.");
        }
        Integer duplicate = firstDuplicate(newLines.stream().map(LineInput::itemId).toList());
        if (duplicate != null) {
            throw new DomainException("Позицію " + duplicate + " указано двічі.");
        }

        lines.clear();
        newLines.forEach(l -> lines.add(new PurchaseOrderLine(l.itemId(), l.quantity(), l.price())));
    }

    /** The order was sent to the supplier. */
    public void markOrdered(LocalDateTime now) {
        requireStatus(DRAFT, "позначити як замовлене");
        status = ORDERED;
        orderedAt = now;
    }

    /** A draft or an ordered one can be cancelled (whatever was already received stays in stock). */
    public void cancel(LocalDateTime now) {
        if (!isOpen()) {
            throw new DomainException("Замовлення №" + id + " вже закрите.");
        }
        status = CANCELLED;
        closedAt = now;
    }

    /**
     * Receiving goods: each receipt line increases the consumable's quantity (a "Прихід за замовленням…" log entry).
     * More than ordered is allowed: suppliers sometimes add extra. When everything has arrived, the order closes.
     */
    public List<StockMovement> receive(Collection<Receipt> receipts, Map<Integer, StockItem> items, int userId,
            String username, LocalDateTime now) {
        requireStatus(ORDERED, "приймати товар");
        if (receipts.isEmpty()) {
            throw new DomainException("Вкажіть, що саме отримано.");
        }
        Integer duplicate = firstDuplicate(receipts.stream().map(Receipt::lineId).toList());
        if (duplicate != null) {
            throw new DomainException("Рядок " + duplicate + " указано двічі.");
        }

        List<StockMovement> movements = new ArrayList<>();
        for (Receipt receipt : receipts) {
            PurchaseOrderLine line = lines.stream()
                    .filter(l -> Objects.equals(l.getId(), receipt.lineId()))
                    .findFirst()
                    .orElseThrow(() -> new DomainException("Рядок " + receipt.lineId() + " не належить замовленню №" + id + "."));
            if (receipt.quantity().signum() <= 0) {
                throw new DomainException("Отримана кількість має бути більшою за нуль.");
            }
            StockItem item = items.get(line.getItemId());
            if (item == null) {
                throw new DomainException("Розхідника " + line.getItemId() + " немає на складі.");
            }

            line.addReceived(receipt.quantity());
            movements.add(item.adjust(receipt.quantity(), "Прихід за замовленням постачальнику №" + id, userId, username, now));
        }

        if (lines.stream().allMatch(l -> l.getRemaining().signum() == 0)) {
            status = RECEIVED;
            closedAt = now;
        }
        return movements;
    }

    private void requireStatus(String required, String action) {
        if (!required.equals(status)) {
            throw new DomainException("Замовлення №" + id + " у статусі " + status + ": " + action + " не можна.");
        }
    }

    private static String commentText(String comment) {
        String text = Text.orEmpty(comment);
        if (text.length() > 500) {
            throw new DomainException("Коментар: не довше 500 символів.");
        }
        return text;
    }

    /** The first value (in list order) that occurs more than once, or null. */
    private static Integer firstDuplicate(List<Integer> values) {
        return values.stream().filter(v -> Collections.frequency(values, v) > 1).findFirst().orElse(null);
    }

    public Integer getId() {
        return id;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public String getStatus() {
        return status;
    }

    public String getComment() {
        return comment;
    }

    public int getCreatedByUserId() {
        return createdByUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    /** When the order was fully received or cancelled. */
    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    /** Read-only view: callers cannot add lines around the rules above. */
    public List<PurchaseOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
