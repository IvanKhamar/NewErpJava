package ua.newerp.application.staff;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import ua.newerp.domain.staff.BonusOrder;

public interface BonusDataSource {

    /** Orders with an assigned manager, created in [start, endExclusive). */
    List<BonusOrder> getManagerOrders(LocalDateTime start, LocalDateTime endExclusive);

    Map<Integer, String> getUserNames(Collection<Integer> userIds);
}
