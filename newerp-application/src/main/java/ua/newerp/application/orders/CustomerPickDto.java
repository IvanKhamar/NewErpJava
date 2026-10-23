package ua.newerp.application.orders;

/** A customer's contacts from their last order. */
public record CustomerPickDto(int customerId, String firstname, String lastname, String telephone, String email, String instagram,
        String city, String address1, String address2, int lastOrderId) {
}
