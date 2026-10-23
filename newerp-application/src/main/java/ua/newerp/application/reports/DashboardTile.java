package ua.newerp.application.reports;

import java.math.BigDecimal;

/** A "now against before" figure: changePercent is null when before was 0. */
public record DashboardTile(BigDecimal current, BigDecimal previous, Integer changePercent) {
}
