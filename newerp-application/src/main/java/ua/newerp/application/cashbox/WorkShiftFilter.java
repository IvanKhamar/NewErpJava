package ua.newerp.application.cashbox;

import java.time.LocalDate;

public record WorkShiftFilter(Integer cashierId, LocalDate from, LocalDate to) {
}
