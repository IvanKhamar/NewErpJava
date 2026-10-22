package ua.newerp.application.delivery;

import java.time.LocalDate;

/** A parcel waits at the branch and paid storage starts soon: the customer should be reminded. */
public record ReminderDto(int orderId, String customer, String telephone, String number, String status, LocalDate datePayedKeeping,
        String managerName) {
}
