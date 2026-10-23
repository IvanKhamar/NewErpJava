package ua.newerp.application.customers;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerDetails(int id, String firstname, String lastname, String email, String telephone, String groupName, boolean status,
        boolean newsletter, LocalDateTime dateAdded, List<CustomerAddressDto> addresses, List<CustomerOrderItem> orders,
        CustomerStatsDto stats, List<CustomerNoteDto> notes) {
}
