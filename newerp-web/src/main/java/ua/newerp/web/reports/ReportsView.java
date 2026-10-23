package ua.newerp.web.reports;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.application.reports.ProductSalesDto;
import ua.newerp.application.reports.ProductSalesRow;
import ua.newerp.application.reports.ReportFilter;
import ua.newerp.application.reports.ReportService;
import ua.newerp.application.reports.SalesReportDto;
import ua.newerp.application.reports.SalesReportRow;
import ua.newerp.domain.reports.ReportGrouping;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/** Sales by period and by product (Reports.razor). Without a status all placed orders count except the cancelled ones. */
@Route("reports")
@PageTitle("Звіти — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class ReportsView extends VerticalLayout {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    final DatePicker from = new DatePicker("З");
    final DatePicker to = new DatePicker("По");
    final Select<OrderStatusDto> status = new Select<>();
    final Select<ReportGrouping> group = new Select<>();
    final Button show = new Button("Показати");
    final Grid<SalesReportRow> sales = new Grid<>();
    final Grid<ProductSalesRow> products = new Grid<>();
    private final ReportService service;
    SalesReportDto salesReport;

    public ReportsView(ReportService service, OrderQueries orders) {
        this.service = service;
        var hint = new Paragraph("Без вибраного статусу враховуються всі оформлені замовлення, крім скасованих.");
        hint.addClassName("secondary");

        for (DatePicker picker : List.of(from, to)) {
            picker.setLocale(Fmt.UK);
            picker.setWidth("10rem");
        }
        status.setLabel("Статус");
        status.setMinWidth("14rem");
        status.setItems(orders.listStatuses());
        status.setEmptySelectionAllowed(true);
        status.setItemLabelGenerator(s -> s == null ? "Усі, крім скасованих" : s.name());
        group.setLabel("Групувати");
        group.setItems(ReportGrouping.values());
        group.setItemLabelGenerator(g -> switch (g) {
            case DAY -> "за днями";
            case WEEK -> "за тижнями";
            case MONTH -> "за місяцями";
            case YEAR -> "за роками";
        });
        group.setValue(ReportGrouping.WEEK);
        show.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        show.addClickListener(e -> load());
        var filters = new FlexLayout(from, to, status, group, show);
        filters.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        filters.setAlignItems(FlexComponent.Alignment.BASELINE);
        filters.addClassName(LumoUtility.Gap.MEDIUM);

        sales.addColumn(ReportsView::period).setHeader("Період").setFlexGrow(1);
        sales.addColumn(SalesReportRow::orders).setHeader("Замовлень").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sales.addColumn(SalesReportRow::products).setHeader("Товарів, шт.").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sales.addColumn(r -> Fmt.money(r.total())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sales.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        sales.setAllRowsVisible(true);
        sales.setPartNameGenerator(r -> r.start() == null ? "total-row" : null);

        products.addColumn(ProductSalesRow::name).setHeader("Товар").setFlexGrow(1);
        products.addColumn(ProductSalesRow::model).setHeader("Модель").setAutoWidth(true);
        products.addColumn(ProductSalesRow::quantity).setHeader("Продано, шт.").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        products.addColumn(r -> Fmt.money(r.total())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        products.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        products.setEmptyStateText("За цей період продажів немає.");
        products.setAllRowsVisible(true);

        var tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("Продажі", sales);
        tabs.add("Товари", products);
        add(new H2("Звіти"), hint, filters, tabs);
        load();
        // The server picks the default period (this month): show it in the filter.
        if (salesReport != null) {
            from.setValue(salesReport.from());
            to.setValue(salesReport.to());
        }
    }

    void load() {
        var filter = new ReportFilter(from.getValue(), to.getValue(), status.getValue() == null ? null : status.getValue().id(),
                group.getValue().name());
        Actions.run(null, () -> {
            salesReport = service.sales(filter);
            ProductSalesDto productReport = service.products(filter);
            // The total goes last, as a row of its own, like in the Blazor table.
            var rows = new ArrayList<>(salesReport.rows());
            rows.add(new SalesReportRow(null, null, salesReport.orders(), salesReport.products(), salesReport.total()));
            sales.setItems(rows);
            products.setItems(productReport.rows());
        });
    }

    static String period(SalesReportRow row) {
        if (row.start() == null) {
            return "Разом";
        }
        return row.start().equals(row.end()) ? row.start().format(DATE) : row.start().format(DATE) + " – " + row.end().format(DATE);
    }
}
