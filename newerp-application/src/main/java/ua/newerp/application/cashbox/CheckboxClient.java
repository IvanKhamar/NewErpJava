package ua.newerp.application.cashbox;

import ua.newerp.domain.cashbox.FiscalReceipt;

/**
 * The Checkbox software cash register API. Every action is on behalf of a cashier (only the implementation knows their login,
 * password and license key). Without keys a stub is registered: it either can do nothing or (checkbox.fake-receipts) issues
 * test receipts, so local development never fiscalizes a real sale. A Checkbox refusal becomes a DomainException with its message.
 */
public interface CheckboxClient {

    /** A test stub, not the real service. */
    boolean isFake();

    /** Whether we can act for the cashier: their keys are set (or test receipts are on). */
    boolean canUse(int cashierId);

    /** The cashier's open shift in Checkbox, or a new one if there is none. Returns the shift id. */
    String openShift(int cashierId);

    CheckboxShiftState getShift(int cashierId, String shiftId);

    /**
     * Closes the cashier's shift (Z report). Returns the Z report id, or null if Checkbox is still closing the shift
     * (closing is asynchronous in Checkbox; then the shift check picks the report up).
     */
    String closeShift(int cashierId);

    /** Creates an X report (intermediate, does not close the shift). Returns the report id. */
    String createXReport(int cashierId);

    String getReportText(int cashierId, String reportId);

    /** Fiscalizes a sale receipt. Returns the receipt id in Checkbox. */
    String sell(int cashierId, FiscalReceipt receipt);

    /** Public receipt page on the Checkbox service (contains no keys); null: the stub has no page. */
    String receiptUrl(String receiptId);
}
