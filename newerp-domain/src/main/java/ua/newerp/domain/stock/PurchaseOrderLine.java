package ua.newerp.domain.stock;

import java.math.BigDecimal;

public class PurchaseOrderLine {

    private Integer id;
    private Integer orderId;
    private int itemId;
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal price = BigDecimal.ZERO;
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    protected PurchaseOrderLine() {
    }

    PurchaseOrderLine(int itemId, BigDecimal quantity, BigDecimal price) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.price = price;
    }

    void addReceived(BigDecimal received) {
        receivedQuantity = receivedQuantity.add(received);
    }

    /** How much we still wait for (0 when everything or more was received). */
    public BigDecimal getRemaining() {
        return quantity.subtract(receivedQuantity).max(BigDecimal.ZERO);
    }

    public Integer getId() {
        return id;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public int getItemId() {
        return itemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }
}
