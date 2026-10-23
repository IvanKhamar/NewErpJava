package ua.newerp.application.returns;

import java.time.LocalDateTime;

public record ReturnListItem(int id, int orderId, String customer, String telephone, String product, String model, int quantity, int statusId,
        String statusName, String actionName, LocalDateTime dateAdded, LocalDateTime dateModified) {
}
