package ua.newerp.application.orders;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.orders.CustomerRisk;

/** level is the CustomerRiskLevel as a number (0 new, 1 green, 2 yellow, 3 red), like the .NET enum. */
public record CustomerRiskDto(int level, int totalOrders, int cancelledOrders, List<Integer> cancelledOrderIds, BigDecimal suspicionRate,
        String message) {

    public static CustomerRiskDto of(CustomerRisk risk) {
        return new CustomerRiskDto(risk.level().ordinal(), risk.totalOrders(), risk.cancelledOrderIds().size(), risk.cancelledOrderIds(),
                risk.suspicionRate(), risk.message());
    }
}
