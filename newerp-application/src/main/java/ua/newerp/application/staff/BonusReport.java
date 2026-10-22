package ua.newerp.application.staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Use {@link #of}: it counts totalBonus (a computed property in .NET, a stored field here so JSON has it). */
public record BonusReport(LocalDate from, LocalDate to, BigDecimal percent, List<ManagerBonusRow> managers, BigDecimal totalBonus) {

    public static BonusReport of(LocalDate from, LocalDate to, BigDecimal percent, List<ManagerBonusRow> managers) {
        return new BonusReport(from, to, percent, managers,
                managers.stream().map(ManagerBonusRow::bonus).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
