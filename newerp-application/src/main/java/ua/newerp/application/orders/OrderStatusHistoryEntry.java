package ua.newerp.application.orders;

import java.time.LocalDateTime;

public record OrderStatusHistoryEntry(LocalDateTime changedAt, int oldStatusId, int newStatusId, String newStatusName,
        int userId, String userName) {
}
