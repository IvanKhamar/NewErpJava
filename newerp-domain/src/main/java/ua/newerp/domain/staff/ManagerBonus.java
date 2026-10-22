package ua.newerp.domain.staff;

import java.math.BigDecimal;

/** Bonus total of one manager for a period. */
public record ManagerBonus(int managerId, int ordersCount, BigDecimal revenue, BigDecimal bonus) {
}
