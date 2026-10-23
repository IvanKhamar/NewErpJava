package ua.newerp.application.customers;

import java.time.LocalDateTime;
import java.util.List;

/** A customer without orders: what the store gives the service. */
public record CustomerProfile(int id, String firstname, String lastname, String email, String telephone, String groupName, boolean status,
        boolean newsletter, LocalDateTime dateAdded, List<CustomerAddressDto> addresses, List<CustomerNoteDto> notes) {
}
