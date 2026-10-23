package ua.newerp.application.cashbox;

import java.time.LocalDateTime;

public record ZReportDto(int id, int cashierId, String cashier, String checkboxReportId, String closedBy, LocalDateTime dateAdded) {
}
