package ua.newerp.application.returns;

import java.time.LocalDateTime;
import java.util.List;

public record ReturnDetails(int id, int orderId, int productId, String customer, String telephone, String email, String product, String model,
        int quantity, boolean opened, int reasonId, String reasonName, int actionId, String actionName, int statusId, String statusName,
        String comment, LocalDateTime dateAdded, LocalDateTime dateModified, List<ReturnHistoryEntry> history) {
}
