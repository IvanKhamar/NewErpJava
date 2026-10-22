package ua.newerp.domain.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entry of a consumable's movement log (table dd_stock_log of the old site).
 * Created only by {@link StockItem#adjust} or {@link StockItem#takeUpTo}.
 */
public class StockMovement {

    /** Use (write-off). The old StockTrack module writes the same. */
    public static final String USE = "use";

    /** Receipt. */
    public static final String RESTOCK = "restock";

    private Integer id;
    private StockItem item;
    private String itemName = "";
    private String action = "";
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal stockBefore = BigDecimal.ZERO;
    private BigDecimal stockAfter = BigDecimal.ZERO;
    private String comment = "";
    private int userId;
    private String username = "";
    private LocalDateTime createdAt;

    protected StockMovement() {
    }

    // Package-private (no modifier): only classes of ua.newerp.domain.stock can call it, like C# internal.
    StockMovement(StockItem item, BigDecimal delta, BigDecimal stockBefore, String comment, int userId, String username,
            LocalDateTime createdAt) {
        this.item = item;
        this.itemName = item.getName();
        this.action = delta.signum() < 0 ? USE : RESTOCK;
        this.quantity = delta.abs();
        this.stockBefore = stockBefore;
        this.stockAfter = item.getQuantity();
        this.comment = comment;
        this.userId = userId;
        this.username = username;
        this.createdAt = createdAt;
    }

    /** Signed change: minus = use, plus = receipt. */
    public BigDecimal getDelta() {
        return USE.equals(action) ? quantity.negate() : quantity;
    }

    public Integer getId() {
        return id;
    }

    public StockItem getItem() {
        return item;
    }

    /** Name of the item at the time of the entry (the old log keeps a copy). */
    public String getItemName() {
        return itemName;
    }

    /** {@link #USE} or {@link #RESTOCK}. */
    public String getAction() {
        return action;
    }

    /** How much was used or received, always positive. */
    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getStockBefore() {
        return stockBefore;
    }

    public BigDecimal getStockAfter() {
        return stockAfter;
    }

    public String getComment() {
        return comment;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
