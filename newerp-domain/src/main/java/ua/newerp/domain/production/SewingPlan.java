package ua.newerp.domain.production;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The sewing plan: what to sew for active orders. The old site only showed the ready date in the order list
 * (order date + the category's production time), and how much to sew was counted by hand.
 */
public final class SewingPlan {

    private SewingPlan() {
    }

    /**
     * The free sewn stock (what orders already wrote off does not count in it) covers the need first; the rest has to be sewn.
     * What is cut gets sewn first, so only what the cut stock does not cover has to be cut.
     * The deadline is the earliest among the orders that miss something.
     */
    public static SewingPlanLine forSize(int sizeId, BigDecimal sewnStock, BigDecimal cutStock, Collection<SewingDemand> demands) {
        List<SewingDemand> list = demands.stream().filter(d -> d.sizeId() == sizeId).toList();
        BigDecimal missing = sum(list.stream().map(SewingDemand::missing).toList());
        BigDecimal toSew = missing.subtract(sewnStock.max(BigDecimal.ZERO)).max(BigDecimal.ZERO);
        BigDecimal fromCut = toSew.min(cutStock.max(BigDecimal.ZERO));
        List<SewingDemand> waiting = list.stream().filter(d -> d.missing().signum() > 0).toList();
        return new SewingPlanLine(
                sizeId,
                sum(list.stream().map(SewingDemand::ordered).toList()),
                missing,
                toSew,
                fromCut,
                toSew.subtract(fromCut),
                waiting.stream().map(SewingDemand::deadline).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null),
                waiting.stream().map(SewingDemand::orderId).distinct().sorted().toList());
    }

    /**
     * The ready date, as in the old site's order list: order date + the category's production time.
     * A time of 0 means "not set" (the old site then showed the order date itself, and everything looked overdue).
     */
    public static LocalDate deadline(LocalDateTime orderDate, int productionDays) {
        return productionDays > 0 ? orderDate.toLocalDate().plusDays(productionDays) : null;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
