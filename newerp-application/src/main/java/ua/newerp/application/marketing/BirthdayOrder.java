package ua.newerp.application.marketing;

import java.time.LocalDateTime;

/** An order with a birthday (raw data for the list of birthday people). */
public record BirthdayOrder(String phone, String firstName, String lastName, String email, String shippingCity, String shippingMethod,
        String instagram, int day, int month, LocalDateTime dateAdded) {
}
