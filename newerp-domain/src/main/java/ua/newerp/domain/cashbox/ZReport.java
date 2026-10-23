package ua.newerp.domain.cashbox;

import java.time.LocalDateTime;

/** A Z report: the shift is closed (dd_cashbox_report). */
public class ZReport {

    private Integer id;
    private int userId;
    private int cashierId;
    private String checkboxReportId = "";
    private LocalDateTime dateAdded;

    protected ZReport() {
    }

    public ZReport(int cashierId, String checkboxReportId, int userId, LocalDateTime createdAt) {
        this.cashierId = cashierId;
        this.checkboxReportId = checkboxReportId;
        this.userId = userId;
        this.dateAdded = createdAt;
    }

    public Integer getId() {
        return id;
    }

    /** Who closed the shift; 0: closed automatically. */
    public int getUserId() {
        return userId;
    }

    public int getCashierId() {
        return cashierId;
    }

    public String getCheckboxReportId() {
        return checkboxReportId;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }
}
