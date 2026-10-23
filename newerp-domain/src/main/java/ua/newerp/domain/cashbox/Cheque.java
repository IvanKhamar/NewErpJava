package ua.newerp.domain.cashbox;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A fiscal receipt for an order (dd_cashbox_cheque). One receipt per order, as on the old site. */
public class Cheque {

    private Integer id;
    private int orderId;
    private int cashierId;
    private int warehouseId;
    private int requisitesId;
    private BigDecimal sum = BigDecimal.ZERO;
    private String checkboxId = "";
    private int workShiftId;
    private int reportId;
    private LocalDateTime dateAdded;

    protected Cheque() {
    }

    public Cheque(int orderId, Cashier cashier, BigDecimal sum, int workShiftId, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.cashierId = cashier.getId();
        this.warehouseId = cashier.getWarehouseId();
        this.requisitesId = cashier.getRequisitesId();
        this.sum = sum;
        this.workShiftId = workShiftId;
        this.dateAdded = createdAt;
    }

    public void markFiscalized(String checkboxId, int workShiftId) {
        this.checkboxId = checkboxId;
        this.workShiftId = workShiftId;
    }

    public Integer getId() {
        return id;
    }

    public int getOrderId() {
        return orderId;
    }

    public int getCashierId() {
        return cashierId;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public int getRequisitesId() {
        return requisitesId;
    }

    /** Sum to pay: the order total minus the prepayment. */
    public BigDecimal getSum() {
        return sum;
    }

    /** Receipt id in Checkbox; empty: not fiscalized yet (Checkbox refused or did not answer). */
    public String getCheckboxId() {
        return checkboxId;
    }

    /** The shift the receipt was issued in; 0: unknown (a receipt from the old site). */
    public int getWorkShiftId() {
        return workShiftId;
    }

    /** The Z report that closed the shift with this receipt; 0: the shift is still open. */
    public int getReportId() {
        return reportId;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }

    public boolean isFiscalized() {
        return !checkboxId.isEmpty();
    }
}
