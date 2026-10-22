package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderListItem(
        int id,
        LocalDateTime dateAdded,
        String customer,
        String telephone,
        BigDecimal total,
        String currencyCode,
        int statusId,
        String statusName,
        String sourceName,
        Integer managerId,
        String managerName) {
}
