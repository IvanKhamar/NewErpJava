package ua.newerp.application.staff;

import java.math.BigDecimal;

public record ManagerBonusRow(int managerId, String managerName, int ordersCount, BigDecimal revenue, BigDecimal bonus) {
}
