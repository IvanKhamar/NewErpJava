package ua.newerp.domain.production;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One order's need for a model size: how much is ordered and how many sewn pieces it already holds (written off the stock). */
public record SewingDemand(int orderId, int sizeId, BigDecimal ordered, BigDecimal held, LocalDate deadline) {

    /** How much this order still misses. It never holds more than ordered, but just in case it does not go negative. */
    public BigDecimal missing() {
        return ordered.subtract(held).max(BigDecimal.ZERO);
    }

    public SewingDemand withHeld(BigDecimal value) {
        return new SewingDemand(orderId, sizeId, ordered, value, deadline);
    }
}
