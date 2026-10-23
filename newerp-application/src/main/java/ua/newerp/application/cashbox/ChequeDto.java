package ua.newerp.application.cashbox;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A receipt. receiptUrl: the receipt page on the Checkbox service (to print it or send it to the customer). */
public record ChequeDto(int id, int orderId, String customer, int cashierId, String cashier, String warehouse, String requisites,
        BigDecimal sum, String checkboxId, boolean isFiscalized, String receiptUrl, int workShiftId, int reportId,
        LocalDateTime dateAdded) {

    public ChequeDto withReceiptUrl(String url) {
        return new ChequeDto(id, orderId, customer, cashierId, cashier, warehouse, requisites, sum, checkboxId, isFiscalized, url,
                workShiftId, reportId, dateAdded);
    }
}
