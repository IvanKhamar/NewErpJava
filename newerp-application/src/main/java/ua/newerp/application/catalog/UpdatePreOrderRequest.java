package ua.newerp.application.catalog;

import java.time.LocalDate;

public record UpdatePreOrderRequest(int quota, LocalDate dateAvailable) {
}
