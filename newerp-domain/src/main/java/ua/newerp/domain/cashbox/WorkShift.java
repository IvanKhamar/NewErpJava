package ua.newerp.domain.cashbox;

import java.time.LocalDateTime;

/** A cashier's work shift (dd_cashbox_work_shift). Receipts can be issued only in an open shift. */
public class WorkShift {

    private Integer id;
    private int userId;
    private int cashierId;
    private String checkboxShiftId = "";
    private LocalDateTime dateAdded;
    private LocalDateTime dateStart;
    private LocalDateTime dateEnd;

    protected WorkShift() {
    }

    public WorkShift(int cashierId, String checkboxShiftId, int openedByUserId, LocalDateTime openedAt) {
        this.cashierId = cashierId;
        this.checkboxShiftId = checkboxShiftId;
        this.userId = openedByUserId;
        this.dateAdded = openedAt;
        this.dateStart = openedAt;
    }

    public void close(LocalDateTime closedAt) {
        if (dateEnd == null) {
            dateEnd = closedAt;
        }
    }

    public Integer getId() {
        return id;
    }

    /** Who opened the shift (dd_user). */
    public int getUserId() {
        return userId;
    }

    public int getCashierId() {
        return cashierId;
    }

    /** Shift id in Checkbox. */
    public String getCheckboxShiftId() {
        return checkboxShiftId;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }

    public LocalDateTime getDateStart() {
        return dateStart;
    }

    public LocalDateTime getDateEnd() {
        return dateEnd;
    }

    public boolean isOpen() {
        return dateEnd == null;
    }
}
