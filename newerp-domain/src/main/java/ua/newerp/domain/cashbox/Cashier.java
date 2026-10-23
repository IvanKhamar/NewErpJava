package ua.newerp.domain.cashbox;

import java.time.LocalDateTime;
import java.time.LocalTime;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;

/**
 * A Checkbox cashier (dd_cashbox_user of the old site). Login, password and the cash register's license key are not here:
 * NewErp keeps them only in settings (application-local.yml), not in the database.
 */
public class Cashier {

    private Integer id;
    private String name = "";
    private String description = "";
    private String paymentCode = "";
    private LocalTime autoCloseTime;
    private int warehouseId;
    private int requisitesId;
    private boolean active;
    private LocalDateTime dateAdded;

    protected Cashier() {
    }

    public Cashier(String name, String description, String paymentCode, LocalTime autoCloseTime, int warehouseId, int requisitesId,
            boolean active, LocalDateTime createdAt) {
        update(name, description, paymentCode, autoCloseTime, warehouseId, requisitesId, active);
        this.dateAdded = createdAt;
    }

    public void update(String name, String description, String paymentCode, LocalTime autoCloseTime, int warehouseId, int requisitesId,
            boolean active) {
        if (name == null || name.isBlank()) {
            throw new DomainException("Вкажіть ім'я касира: воно друкується в чеку.");
        }
        this.name = name.strip();
        this.description = Text.orEmpty(description);
        this.paymentCode = Text.orEmpty(paymentCode);
        this.autoCloseTime = autoCloseTime;
        this.warehouseId = warehouseId;
        this.requisitesId = requisitesId;
        this.active = active;
    }

    public Integer getId() {
        return id;
    }

    /** The cashier's name: it is printed on the receipt. */
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** OpenCart payment code (cod, liqpay...) whose orders go to this cashier. Empty: the default cashier. */
    public String getPaymentCode() {
        return paymentCode;
    }

    /** When to close the shift automatically (Z report); null: never. */
    public LocalTime getAutoCloseTime() {
        return autoCloseTime;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public int getRequisitesId() {
        return requisitesId;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }
}
