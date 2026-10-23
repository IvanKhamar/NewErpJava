package ua.newerp.domain.stock;

import static ua.newerp.domain.common.Decimals.text;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;

/**
 * Where a consumable lies. dd_stock_items.stock stays the total, because the old site and every other operation (orders,
 * production, stocktaking) work with it. The warehouses hold their balances, and the main stock is what is left:
 * total − all balances. So whatever does not name a warehouse takes from or adds to the main stock.
 * A null warehouse id means the main stock everywhere here.
 */
public final class StockPlacement {

    private final StockItem item;
    private final Map<Integer, StockBalance> balances = new LinkedHashMap<>();
    private final List<StockBalance> created = new ArrayList<>();

    public StockPlacement(StockItem item, Collection<StockBalance> balances) {
        this.item = item;
        balances.forEach(b -> this.balances.put(b.getWarehouseId(), b));
    }

    /** The main stock: the total minus what lies at the warehouses. Negative when the total went down past it elsewhere. */
    public BigDecimal main() {
        return item.getQuantity().subtract(located());
    }

    /** Balance at a warehouse, or the main stock for null. */
    public BigDecimal at(Integer warehouseId) {
        if (warehouseId == null) {
            return main();
        }
        StockBalance balance = balances.get(warehouseId);
        return balance == null ? BigDecimal.ZERO : balance.getQuantity();
    }

    /** Whether some warehouse still holds anything (then the item cannot be archived). */
    public boolean isAtWarehouses() {
        return balances.values().stream().anyMatch(b -> b.getQuantity().signum() != 0);
    }

    /** Moves {@code quantity} between two places; the total stays. Not more than the source has. */
    public StockTransfer move(Integer from, Integer to, BigDecimal quantity, String comment, int userId, String username,
            LocalDateTime now) {
        if (Objects.equals(from, to)) {
            throw new DomainException("Звідки й куди переміщують — має бути різне місце.");
        }
        requireQuantity(quantity);
        String text = Text.orEmpty(comment);
        if (text.length() > 255) {
            throw new DomainException("Коментар: не довше 255 символів.");
        }
        requireAvailable(from, quantity);
        change(from, quantity.negate());
        change(to, quantity);
        return new StockTransfer(item.getId(), from, to, quantity, text, userId, username, now);
    }

    /**
     * Receipt (+) or use (−) at one place. The total changes too, so it goes into the item's movement log like any
     * adjustment. Use at a place is not more than that place has.
     */
    public StockMovement adjustAt(Integer warehouseId, BigDecimal delta, String comment, int userId, String username,
            LocalDateTime now) {
        // With nothing at the warehouses the main stock is the total: the item's own check and message apply
        if (delta.signum() < 0 && (warehouseId != null || located().signum() != 0)) {
            requireAvailable(warehouseId, delta.negate());
        }
        StockMovement movement = item.adjust(delta, comment, userId, username, now);
        if (warehouseId != null) {
            change(warehouseId, delta);
        }
        return movement;
    }

    /**
     * Stocktaking of one place: its quantity becomes what was counted, and the item's total changes by the same difference
     * (a log entry). Returns null when the count matches.
     */
    public StockMovement recountAt(Integer warehouseId, BigDecimal actual, String comment, int userId, String username,
            LocalDateTime now) {
        Stocktaking.requireValid(actual);
        BigDecimal delta = actual.subtract(at(warehouseId));
        if (delta.signum() == 0) {
            return null;
        }
        StockMovement movement = item.recount(item.getQuantity().add(delta), comment, userId, username, now);
        change(warehouseId, delta);
        return movement;
    }

    /** A receipt that already went into the item's total (a supplier delivery) lies at this warehouse, not on the main stock. */
    public void receivedAt(int warehouseId, BigDecimal quantity) {
        requireQuantity(quantity);
        change(warehouseId, quantity);
    }

    /** Balances made by this placement for warehouses that had none yet: to be saved. */
    public List<StockBalance> created() {
        return List.copyOf(created);
    }

    public StockItem item() {
        return item;
    }

    public Collection<StockBalance> balances() {
        return List.copyOf(balances.values());
    }

    private BigDecimal located() {
        return balances.values().stream().map(StockBalance::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void change(Integer warehouseId, BigDecimal delta) {
        if (warehouseId == null) {
            return; // the main stock is the remainder: it follows by itself
        }
        balances.computeIfAbsent(warehouseId, id -> {
            var balance = new StockBalance(item.getId(), id);
            created.add(balance);
            return balance;
        }).add(delta);
    }

    private void requireAvailable(Integer warehouseId, BigDecimal quantity) {
        BigDecimal available = at(warehouseId).max(BigDecimal.ZERO);
        if (available.compareTo(quantity) < 0) {
            String place = warehouseId == null ? "на основному складі" : "на цьому складі";
            throw new DomainException("Недостатньо «" + item.getName() + "»: " + place + " є " + text(available) + " "
                    + item.getUnit() + ", потрібно " + text(quantity) + " " + item.getUnit() + ".");
        }
    }

    private static void requireQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3) {
            throw new DomainException("Кількість має бути більшою за нуль, не більше 3 знаків після коми.");
        }
    }
}
