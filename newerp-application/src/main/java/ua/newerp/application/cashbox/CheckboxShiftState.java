package ua.newerp.application.cashbox;

/** The shift on the Checkbox side. zReportId: set when the shift is already closed (for example, by the register itself). */
public record CheckboxShiftState(String shiftId, boolean isClosed, String zReportId) {
}
