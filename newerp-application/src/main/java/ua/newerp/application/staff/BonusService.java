package ua.newerp.application.staff;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.staff.BonusPolicy;
import ua.newerp.domain.staff.ManagerBonus;

@Service
public class BonusService {

    private final BonusDataSource source;
    private final BonusProperties properties;

    public BonusService(BonusDataSource source, BonusProperties properties) {
        this.source = source;
        this.properties = properties;
    }

    public BonusReport calculate(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new DomainException("Дата кінця періоду раніша за дату початку.");
        }

        var policy = new BonusPolicy(properties.percent(), properties.countedStatusIds());

        // The period includes both dates, so the upper bound is the day after "to".
        var orders = source.getManagerOrders(from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        List<ManagerBonus> bonuses = policy.calculate(orders);
        Map<Integer, String> names = source.getUserNames(bonuses.stream().map(ManagerBonus::managerId).toList());

        List<ManagerBonusRow> rows = bonuses.stream()
                .map(b -> new ManagerBonusRow(b.managerId(), names.get(b.managerId()), b.ordersCount(), b.revenue(), b.bonus()))
                .toList();
        return BonusReport.of(from, to, policy.getPercent(), rows);
    }
}
