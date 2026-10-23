package ua.newerp.application.customers;

import java.time.LocalDateTime;

public record CustomerListItem(int id, String name, String email, String telephone, String groupName, boolean status,
        LocalDateTime dateAdded, int orderCount) {
}
