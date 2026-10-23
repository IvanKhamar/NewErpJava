package ua.newerp.domain.cashbox;

import java.time.LocalDateTime;

/** A warehouse or point of sale of the cash register (dd_cashbox_warehouse). */
public class CashboxWarehouse extends CashboxDirectoryEntry {

    protected CashboxWarehouse() {
    }

    public CashboxWarehouse(String name, String description, LocalDateTime createdAt) {
        super(name, description, createdAt);
    }
}
