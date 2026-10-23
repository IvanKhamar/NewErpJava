package ua.newerp.application.cashbox;

import java.util.List;

/** Result of closing shifts automatically: how many were checked, closed by the register itself, and closed by us on schedule. */
public record AutoCloseResult(int checked, int closedByCheckbox, int closedBySchedule, List<String> errors) {
}
