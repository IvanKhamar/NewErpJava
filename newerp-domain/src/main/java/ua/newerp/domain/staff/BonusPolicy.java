package ua.newerp.domain.staff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ua.newerp.domain.common.DomainException;

/**
 * Bonus rule: a percent of the total of orders in "counted" statuses.
 * This is our simplified rule. The exact formula of the old user/bonus module still has to be checked.
 */
public final class BonusPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal percent;
    private final Set<Integer> countedStatusIds;

    public BonusPolicy(BigDecimal percent, Collection<Integer> countedStatusIds) {
        if (percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
            throw new DomainException("Відсоток бонусу має бути від 0 до 100.");
        }
        this.percent = percent;
        this.countedStatusIds = Set.copyOf(new LinkedHashSet<>(countedStatusIds));
        if (this.countedStatusIds.isEmpty()) {
            throw new DomainException("Вкажіть хоча б один статус, за який нараховується бонус.");
        }
    }

    /** Streams are Java's LINQ: filter = Where, map = Select, collect(groupingBy) = GroupBy. */
    public List<ManagerBonus> calculate(Collection<BonusOrder> orders) {
        Map<Integer, List<BonusOrder>> byManager = orders.stream()
                .filter(o -> countedStatusIds.contains(o.statusId()))
                .collect(Collectors.groupingBy(BonusOrder::managerId, LinkedHashMap::new, Collectors.toList()));

        return byManager.entrySet().stream()
                .map(e -> {
                    BigDecimal revenue = e.getValue().stream().map(BonusOrder::total).reduce(BigDecimal.ZERO, BigDecimal::add);
                    // HALF_UP = MidpointRounding.AwayFromZero
                    BigDecimal bonus = revenue.multiply(percent).divide(HUNDRED).setScale(2, RoundingMode.HALF_UP);
                    return new ManagerBonus(e.getKey(), e.getValue().size(), revenue, bonus);
                })
                .sorted(Comparator.comparing(ManagerBonus::bonus).reversed())
                .toList();
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public Set<Integer> getCountedStatusIds() {
        return countedStatusIds;
    }
}
