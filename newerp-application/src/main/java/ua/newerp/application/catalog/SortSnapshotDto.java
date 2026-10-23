package ua.newerp.application.catalog;

import java.time.LocalDateTime;

public record SortSnapshotDto(int id, String note, LocalDateTime createdAt) {
}
