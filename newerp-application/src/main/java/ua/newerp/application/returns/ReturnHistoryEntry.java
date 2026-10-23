package ua.newerp.application.returns;

import java.time.LocalDateTime;

public record ReturnHistoryEntry(LocalDateTime dateAdded, int statusId, String statusName, String comment) {
}
