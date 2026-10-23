package ua.newerp.application.catalog;

/** What measures popularity in the cross-sell panel: all sales, or only those that came through the panel. */
public record CrossSellMetricDto(boolean panelOnly) {
}
