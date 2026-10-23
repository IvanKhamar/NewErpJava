package ua.newerp.domain.cashbox;

import java.time.LocalDateTime;

/** Sole proprietor details the receipt is issued for (dd_cashbox_requisites). */
public class CashboxRequisites extends CashboxDirectoryEntry {

    protected CashboxRequisites() {
    }

    public CashboxRequisites(String name, String description, LocalDateTime createdAt) {
        super(name, description, createdAt);
    }
}
