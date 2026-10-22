package ua.newerp.domain.stock;

import static ua.newerp.domain.common.Decimals.isNegative;
import static ua.newerp.domain.common.Decimals.less;
import static ua.newerp.domain.common.Decimals.text;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;

/**
 * A consumable in stock (fabric, fittings, packaging...), table dd_stock_items of the old site.
 * The quantity changes only through {@link #adjust} and {@link #takeUpTo}, so every change gets into the log.
 */
public class StockItem {

    private Integer id;
    private Integer categoryId;
    private Integer supplierId;
    private String name = "";
    private String unit = "";
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal minQuantity = BigDecimal.ZERO;
    private String usedFor = "";
    private BigDecimal price = BigDecimal.ZERO;
    private boolean active = true;

    /** For JPA only: it creates the object empty and then fills the fields from the row. */
    protected StockItem() {
    }

    public StockItem(Integer categoryId, Integer supplierId, String name, String unit, BigDecimal minQuantity,
            String usedFor, BigDecimal price) {
        update(categoryId, supplierId, name, unit, minQuantity, usedFor, price);
    }

    /** Changes the description. The quantity does not change here: only adjust and takeUpTo, with a log entry. */
    public void update(Integer categoryId, Integer supplierId, String name, String unit, BigDecimal minQuantity,
            String usedFor, BigDecimal price) {
        if (isNegative(price)) {
            throw new DomainException("Ціна не може бути від'ємною.");
        }
        this.categoryId = categoryId;
        this.supplierId = supplierId;
        this.name = Text.require(name, "Назва позиції", 150);
        this.unit = Text.require(unit, "Одиниця виміру", 16);
        this.usedFor = Text.orEmpty(usedFor);
        this.price = price;
        setMinQuantity(minQuantity);
    }

    /**
     * Manual change of the quantity by {@code delta} (plus = receipt, minus = use).
     * Stricter than the old module: you cannot write off more than there is.
     */
    public StockMovement adjust(BigDecimal delta, String comment, int userId, String username, LocalDateTime now) {
        if (delta.signum() == 0) {
            throw new DomainException("Зміна залишку не може дорівнювати нулю.");
        }
        if (isNegative(quantity.add(delta))) {
            throw new DomainException("Недостатньо залишку: є " + text(quantity) + " " + unit
                    + ", списується " + text(delta.negate()) + " " + unit + ".");
        }
        return change(delta, Text.require(comment, "Причина", 255), userId, username, now);
    }

    /**
     * Write-off for production, as in the old module: if there is not enough, it takes what there is
     * and the quantity becomes zero. Returns null when there is nothing to take.
     */
    public StockMovement takeUpTo(BigDecimal wanted, String comment, int userId, String username, LocalDateTime now) {
        if (wanted.signum() <= 0) {
            throw new DomainException("Кількість для списання має бути більшою за нуль.");
        }
        BigDecimal amount = wanted.min(quantity);
        return amount.signum() > 0 ? change(amount.negate(), Text.require(comment, "Причина", 255), userId, username, now) : null;
    }

    public void setMinQuantity(BigDecimal minQuantity) {
        if (isNegative(minQuantity)) {
            throw new DomainException("Мінімальний залишок не може бути від'ємним.");
        }
        this.minQuantity = minQuantity;
    }

    private StockMovement change(BigDecimal delta, String comment, int userId, String username, LocalDateTime now) {
        BigDecimal before = quantity;
        quantity = quantity.add(delta);
        return new StockMovement(this, delta, before, comment, userId, username, now);
    }

    public boolean isBelowMinimum() {
        return less(quantity, minQuantity);
    }

    public Integer getId() {
        return id;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public Integer getSupplierId() {
        return supplierId;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getMinQuantity() {
        return minQuantity;
    }

    /** What it is used for (free text from the old module). */
    public String getUsedFor() {
        return usedFor;
    }

    /** Price per unit. */
    public BigDecimal getPrice() {
        return price;
    }

    /** false means "deleted": the old module does not delete rows, it sets status = 0. */
    public boolean isActive() {
        return active;
    }
}
