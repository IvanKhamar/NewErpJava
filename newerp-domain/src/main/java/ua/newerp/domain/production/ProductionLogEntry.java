package ua.newerp.domain.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Production log entry (dd_stock_production_log): change of cut or sewn stock of one size.
 * {@code stage} and {@code direction} have the same values the old StockTrack module writes.
 */
public class ProductionLogEntry {

    public static final String CUT = "cut";
    public static final String SEWN = "sewn";
    public static final String IN = "in";
    public static final String OUT = "out";

    private Integer id;
    // References to the model and size let JPA fill in the ids of a new model or size when saving.
    private ProductionModel model;
    private ProductionSize size;
    private String sizeName = "";
    private String stage = "";
    private String direction = "";
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal stockBefore = BigDecimal.ZERO;
    private BigDecimal stockAfter = BigDecimal.ZERO;
    private String comment = "";
    private int userId;
    private String username = "";
    private LocalDateTime createdAt;

    protected ProductionLogEntry() {
    }

    ProductionLogEntry(ProductionModel model, ProductionSize size, String stage, BigDecimal delta, BigDecimal before,
            BigDecimal after, String comment, int userId, String username, LocalDateTime createdAt) {
        this.model = model;
        this.size = size;
        this.sizeName = size.getName();
        this.stage = stage;
        this.direction = delta.signum() > 0 ? IN : OUT;
        this.quantity = delta.abs();
        this.stockBefore = before;
        this.stockAfter = after;
        this.comment = comment;
        this.userId = userId;
        this.username = username;
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public ProductionModel getModel() {
        return model;
    }

    public ProductionSize getSize() {
        return size;
    }

    public String getSizeName() {
        return sizeName;
    }

    /** {@link #CUT} or {@link #SEWN}. */
    public String getStage() {
        return stage;
    }

    /** {@link #IN} or {@link #OUT}. */
    public String getDirection() {
        return direction;
    }

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
