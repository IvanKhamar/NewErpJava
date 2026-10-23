package ua.newerp.domain.marketing;

/** An export row: first and last name and city come from this contact's latest order. */
public record ContactRow(String firstName, String lastName, String city, String phone, String email) {
}
