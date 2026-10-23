package ua.newerp.application.customers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Totals by orders; riskLevel ("New", "Green", "Yellow", "Red") is the same check as in the new order form. */
public record CustomerStatsDto(int orderCount, int cancelledCount, BigDecimal spent, LocalDateTime firstOrder, LocalDateTime lastOrder,
        String riskLevel, String riskMessage) {
}
