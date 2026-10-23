package ua.newerp.application.customers;

import java.time.LocalDateTime;

public record CustomerNoteDto(LocalDateTime dateAdded, String comment) {
}
