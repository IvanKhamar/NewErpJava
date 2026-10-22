package ua.newerp.domain.staff;

import java.math.BigDecimal;

/** A manager's order that may give a bonus. */
public record BonusOrder(int orderId, int managerId, int statusId, BigDecimal total) {
}
