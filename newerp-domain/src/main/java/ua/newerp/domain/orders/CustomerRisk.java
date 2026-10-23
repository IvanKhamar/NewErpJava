package ua.newerp.domain.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * How far a customer can be trusted, by their previous orders (like the old site's "Fraud Check"):
 * the bigger the share of cancelled orders, the higher the risk.
 */
public record CustomerRisk(CustomerRiskLevel level, int totalOrders, List<Integer> cancelledOrderIds, BigDecimal suspicionRate,
        String message) {

    /** Id of the "Відмінено" status on the real site. */
    public static final int CANCELLED_STATUS_ID = 19;

    /** How many years of history count. */
    public static final int LOOKBACK_YEARS = 4;

    private static final BigDecimal HALF = BigDecimal.valueOf(50);

    public static CustomerRisk from(List<CustomerOrder> orders) {
        if (orders.isEmpty()) {
            return new CustomerRisk(CustomerRiskLevel.NEW, 0, List.of(), BigDecimal.ZERO, "Новий клієнт. Замовлень не знайдено.");
        }

        List<Integer> cancelled = orders.stream().filter(o -> o.statusId() == CANCELLED_STATUS_ID).map(CustomerOrder::orderId).toList();
        BigDecimal rate = BigDecimal.valueOf(cancelled.size() * 100L)
                .divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_EVEN);

        if (cancelled.isEmpty()) {
            return new CustomerRisk(CustomerRiskLevel.GREEN, orders.size(), cancelled, rate, "Клієнт надійний. Скасувань немає.");
        }
        if (rate.compareTo(HALF) < 0) {
            return new CustomerRisk(CustomerRiskLevel.YELLOW, orders.size(), cancelled, rate,
                    "Увага: є скасовані замовлення. Рекомендовано перевірити.");
        }
        return new CustomerRisk(CustomerRiskLevel.RED, orders.size(), cancelled, rate,
                "Потенційний фрод-ризик. Рекомендовано вимагати передоплату.");
    }

    /** The last 9 digits of a phone: so "+38 (067) 123-45-67" and "0671234567" count as one number. Null when shorter. */
    public static String phoneKey(String telephone) {
        String digits = telephone == null ? "" : telephone.replaceAll("[^0-9]", "");
        return digits.length() >= 9 ? digits.substring(digits.length() - 9) : null;
    }
}
