package ua.newerp.domain.marketing;

import java.time.LocalDateTime;

/** A contact's data from one order. */
public record OrderContact(String phone, String email, String firstName, String lastName, String city, LocalDateTime dateAdded) {
}
