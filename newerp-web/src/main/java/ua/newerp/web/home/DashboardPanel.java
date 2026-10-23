package ua.newerp.web.home;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.reports.DashboardDto;
import ua.newerp.application.reports.DashboardTile;
import ua.newerp.application.reports.ProductSalesRow;
import ua.newerp.application.reports.SalesReportRow;
import ua.newerp.web.Fmt;
import ua.newerp.web.reports.ReportsView;
import ua.newerp.web.ui.InfoTable;
import ua.newerp.web.ui.Panel;

/** The sales summary on the home page (DashboardPanel.razor): tiles, a 30-day chart and the top products. */
class DashboardPanel extends Div {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");

    DashboardPanel(DashboardDto data) {
        setWidthFull();
        var tiles = new Div(
                tile("Замовлень сьогодні", data.ordersToday(), "вчора", false),
                tile("Продажі сьогодні", data.salesToday(), "вчора", true),
                tile("Замовлень за місяць", data.ordersMonth(), "ті самі дні минулого місяця", false),
                tile("Продажі за місяць", data.salesMonth(), "ті самі дні минулого місяця", true),
                tile("Середній чек за місяць", data.averageCheckMonth(), "минулий місяць", true),
                tile("Нових покупців за місяць", data.newCustomersMonth(), "минулий місяць", false));
        tiles.addClassName("dashboard-tiles");

        var top = new InfoTable();
        for (ProductSalesRow product : data.topProducts30Days()) {
            top.row(product.name(), product.quantity() + " шт.");
        }
        var all = new RouterLink("Усі звіти", ReportsView.class);
        var columns = new Div(new Panel("Продажі за 30 днів", chart(data.last30Days())), new Panel("Топ товарів за 30 днів", top, all));
        columns.addClassName("dashboard-columns");
        add(tiles, columns);
    }

    private static Component tile(String title, DashboardTile tile, String previousLabel, boolean money) {
        var caption = new Span(title);
        caption.addClassNames("secondary", LumoUtility.FontSize.SMALL);
        var value = new H4(money ? Fmt.money(tile.current()) : Fmt.qty(tile.current()));
        Span change;
        if (tile.changePercent() != null) {
            int percent = tile.changePercent();
            change = new Span((percent > 0 ? "+" : "") + percent + "% проти " + previousLabel);
            change.addClassName(percent >= 0 ? LumoUtility.TextColor.SUCCESS : LumoUtility.TextColor.ERROR);
        } else {
            change = new Span(previousLabel + ": " + (money ? Fmt.money(tile.previous()) : Fmt.qty(tile.previous())));
            change.addClassName("secondary");
        }
        change.addClassName(LumoUtility.FontSize.SMALL);
        var panel = new Div(new Div(caption), value, change);
        panel.addClassName("panel");
        return panel;
    }

    /** Bars in plain HTML, like the Blazor page: the height is the day's sales against the best day. */
    private static Component chart(List<SalesReportRow> days) {
        BigDecimal max = days.stream().map(SalesReportRow::total).reduce(BigDecimal.ONE, BigDecimal::max);
        var bars = new Div();
        bars.addClassName("dashboard-chart");
        for (SalesReportRow day : days) {
            var bar = new Div();
            bar.addClassName("bar");
            bar.getStyle().set("height", day.total().multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP).toPlainString() + "%");
            var slot = new Div(bar);
            slot.addClassName("slot");
            slot.setTitle(day.start().format(DAY) + ": " + day.orders() + " зам., " + Fmt.money(day.total()));
            bars.add(slot);
        }
        var axis = new HorizontalLayout();
        axis.setWidthFull();
        axis.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        if (!days.isEmpty()) {
            axis.add(caption(days.getFirst().start().format(DAY)), caption(days.getLast().start().format(DAY)));
        }
        return new Div(bars, axis);
    }

    private static Span caption(String text) {
        var span = new Span(text);
        span.addClassNames("secondary", LumoUtility.FontSize.XSMALL);
        return span;
    }
}
