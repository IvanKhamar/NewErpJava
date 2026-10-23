package ua.newerp.domain.production;

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
 * Where the sewn pieces of a model lie, size by size. As with consumables, the size's sewn_stock stays the total (the old
 * site, orders and the storefront use it), warehouses hold their balances, and the main stock is the rest. A null warehouse
 * id means the main stock.
 */
public final class SewnPlacement {

    private final ProductionModel model;
    private final Map<Integer, Map<Integer, SewnBalance>> balances = new LinkedHashMap<>();
    private final List<SewnBalance> created = new ArrayList<>();

    public SewnPlacement(ProductionModel model, Collection<SewnBalance> balances) {
        this.model = model;
        balances.forEach(b -> this.balances.computeIfAbsent(b.getSizeId(), k -> new LinkedHashMap<>()).put(b.getWarehouseId(), b));
    }

    /** Sewn pieces of the size at a warehouse, or on the main stock for null. */
    public BigDecimal at(int sizeId, Integer warehouseId) {
        ProductionSize size = model.requireSize(sizeId);
        Map<Integer, SewnBalance> ofSize = balances.getOrDefault(sizeId, Map.of());
        if (warehouseId == null) {
            return size.getSewnStock().subtract(ofSize.values().stream().map(SewnBalance::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        SewnBalance balance = ofSize.get(warehouseId);
        return balance == null ? BigDecimal.ZERO : balance.getQuantity();
    }

    /** Whether some warehouse still holds pieces of any size. */
    public boolean isAtWarehouses() {
        return balances.values().stream().flatMap(m -> m.values().stream()).anyMatch(b -> b.getQuantity().signum() != 0);
    }

    /** Moves sewn pieces of a size between two places; the sewn stock stays. Not more than the source has. */
    public SewnTransfer move(int sizeId, Integer from, Integer to, BigDecimal quantity, String comment, int userId, String username,
            LocalDateTime now) {
        if (Objects.equals(from, to)) {
            throw new DomainException("Звідки й куди переміщують — має бути різне місце.");
        }
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3) {
            throw new DomainException("Кількість має бути більшою за нуль, не більше 3 знаків після коми.");
        }
        String text = Text.orEmpty(comment);
        if (text.length() > 255) {
            throw new DomainException("Коментар: не довше 255 символів.");
        }
        requireAvailable(sizeId, from, quantity);
        change(sizeId, from, quantity.negate());
        change(sizeId, to, quantity);
        return new SewnTransfer(model.getId(), sizeId, from, to, quantity, text, userId, username, now);
    }

    /** Finished pieces arrived at a warehouse (or the main stock): the sewn stock grows and the log gets an entry. */
    public ProductionLogEntry sewnAt(int sizeId, Integer warehouseId, BigDecimal quantity, String comment, int userId, String username,
            LocalDateTime now) {
        ProductionLogEntry entry = model.recordSewn(sizeId, quantity, comment, userId, username, now);
        change(sizeId, warehouseId, quantity);
        return entry;
    }

    /** Pieces left a place not through an order. Not more than that place has. */
    public ProductionLogEntry shipFrom(int sizeId, Integer warehouseId, BigDecimal quantity, String comment, int userId,
            String username, LocalDateTime now) {
        if (quantity != null && quantity.signum() > 0 && (warehouseId != null || isAtWarehouses(sizeId))) {
            requireAvailable(sizeId, warehouseId, quantity);
        }
        ProductionLogEntry entry = model.ship(sizeId, quantity, comment, userId, username, now);
        change(sizeId, warehouseId, quantity.negate());
        return entry;
    }

    /**
     * Stocktaking of the sewn pieces of a size at one place: that place gets what was counted, and the sewn stock changes by the
     * same difference (a log entry). Returns null when the count matches.
     */
    public ProductionLogEntry recountAt(int sizeId, Integer warehouseId, BigDecimal actual, String comment, int userId,
            String username, LocalDateTime now) {
        ProductionSize size = model.requireSize(sizeId);
        if (actual == null || actual.signum() < 0 || actual.stripTrailingZeros().scale() > 3) {
            throw new DomainException("Розмір " + size.getName() + ": кількість має бути від 0, не більше 3 знаків після коми.");
        }
        BigDecimal delta = actual.subtract(at(sizeId, warehouseId));
        if (delta.signum() == 0) {
            return null;
        }
        ProductionLogEntry entry = model.recount(sizeId, ProductionLogEntry.SEWN, size.getSewnStock().add(delta).max(BigDecimal.ZERO),
                comment, userId, username, now);
        change(sizeId, warehouseId, delta);
        return entry;
    }

    /** Balances made for warehouses that had none of the size yet: to be saved. */
    public List<SewnBalance> created() {
        return List.copyOf(created);
    }

    /** The size's balances, also those just made. */
    public Collection<SewnBalance> balances(int sizeId) {
        return List.copyOf(balances.getOrDefault(sizeId, Map.of()).values());
    }

    public ProductionModel model() {
        return model;
    }

    private boolean isAtWarehouses(int sizeId) {
        return balances.getOrDefault(sizeId, Map.of()).values().stream().anyMatch(b -> b.getQuantity().signum() != 0);
    }

    private void change(int sizeId, Integer warehouseId, BigDecimal delta) {
        if (warehouseId == null) {
            return; // the main stock is the remainder: it follows by itself
        }
        balances.computeIfAbsent(sizeId, k -> new LinkedHashMap<>()).computeIfAbsent(warehouseId, id -> {
            var balance = new SewnBalance(sizeId, id);
            created.add(balance);
            return balance;
        }).add(delta);
    }

    private void requireAvailable(int sizeId, Integer warehouseId, BigDecimal quantity) {
        BigDecimal available = at(sizeId, warehouseId).max(BigDecimal.ZERO);
        if (available.compareTo(quantity) < 0) {
            String place = warehouseId == null ? "на основному складі" : "на цьому складі";
            throw new DomainException("Розмір " + model.requireSize(sizeId).getName() + ": " + place + " пошито " + text(available)
                    + ", потрібно " + text(quantity) + ".");
        }
    }
}
