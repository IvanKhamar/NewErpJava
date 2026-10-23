package ua.newerp.web.stock;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.stock.CategoryValueDto;
import ua.newerp.application.stock.ItemConsumptionDto;
import ua.newerp.application.stock.ModelOutputDto;
import ua.newerp.application.stock.SizeOutputDto;
import ua.newerp.application.stock.StockReportDto;
import ua.newerp.application.stock.StockReportService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;

/**
 * Stock reports: what the stock is worth, how fast each consumable goes and what to order for the next weeks at that pace,
 * and what production cut, sewed and sold in the period.
 */
@Route("stock/reports")
@PageTitle("Звіти складу — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class StockReportsView extends VerticalLayout {

    /** One size of a model; the model name is shown on its first size only. */
    record OutputRow(ModelOutputDto model, SizeOutputDto size, boolean first) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    final Select<Integer> days = new Select<>();
    final Select<Integer> coverDays = new Select<>();
    final Grid<CategoryValueDto> values = new Grid<>();
    final Grid<ItemConsumptionDto> consumption = new Grid<>();
    final Grid<OutputRow> output = new Grid<>();
    private final H3 valueTitle = new H3();
    private final H3 consumptionTitle = new H3();
    private final Paragraph noConsumption = new Paragraph("За цей період розхідники не витрачались і не надходили.");
    private final Paragraph noOutput = new Paragraph("За цей період нічого не кроїли, не шили й не продавали.");
    private final StockReportService service;
    StockReportDto report;

    public StockReportsView(StockReportService service) {
        this.service = service;
        days.setLabel("Період");
        days.setItems(7, 30, 90, 180, 365);
        days.setItemLabelGenerator(d -> "Останні " + d + " дн.");
        days.setValue(30);
        coverDays.setLabel("Замовити запас на");
        coverDays.setItems(14, 30, 60, 90);
        coverDays.setItemLabelGenerator(d -> d + " дн.");
        coverDays.setValue(30);
        days.addValueChangeListener(e -> load());
        coverDays.addValueChangeListener(e -> load());

        values.addColumn(CategoryValueDto::name).setHeader("Категорія").setFlexGrow(1);
        values.addColumn(CategoryValueDto::items).setHeader("Позицій").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        values.addColumn(c -> Fmt.money(c.value())).setHeader("Вартість").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        values.addThemeVariants(GridVariant.LUMO_COMPACT);
        values.setAllRowsVisible(true);
        values.setMaxWidth("40rem");

        var paceHint = new Paragraph("Витрата без інвентаризації: різницю, знайдену під час підрахунку, видно окремо. "
                + "Темп — середня витрата за день періоду; «Замовити» — скільки докупити, щоб із тим, що є й уже замовлено, "
                + "вистачило на вибрану кількість днів.");
        paceHint.addClassName("secondary");
        consumption.addComponentColumn(i -> new RouterLink(i.name(), StockItemView.class,
                new RouteParameters("id", String.valueOf(i.itemId())))).setHeader("Розхідник").setAutoWidth(true).setFlexGrow(1);
        consumption.addColumn(i -> Fmt.qty(i.used()) + " " + i.unit()).setHeader("Витрачено").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        consumption.addColumn(i -> signedOrBlank(i.received())).setHeader("Надійшло").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        consumption.addColumn(i -> signedOrBlank(i.corrected())).setHeader("Інвентаризація").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        consumption.addColumn(i -> Fmt.qty(i.perDay())).setHeader("За день").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        consumption.addColumn(i -> Fmt.qty(i.quantity())).setHeader("Залишок").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        consumption.addComponentColumn(i -> {
            var left = new Span(i.daysLeft() == null ? "—" : i.daysLeft() + " дн.");
            if (i.daysLeft() != null && i.daysLeft() < report.coverDays()) {
                left.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.FontWeight.BOLD);
            }
            return left;
        }).setHeader("Вистачить на").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        consumption.addColumn(i -> i.onOrder().signum() > 0 ? Fmt.qty(i.onOrder()) : "").setHeader("Вже замовлено")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        consumption.addColumn(i -> i.suggested().signum() > 0 ? Fmt.qty(i.suggested()) + " " + i.unit() : "")
                .setHeader("Замовити").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        consumption.addColumn(i -> i.sum().signum() > 0 ? Fmt.money(i.sum()) : "").setHeader("Сума").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        consumption.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        consumption.setAllRowsVisible(true);

        output.addComponentColumn(r -> r.first() ? new Anchor("production/models/" + r.model().productionId(), r.model().name()) : new Span())
                .setHeader("Модель").setAutoWidth(true);
        output.addColumn(r -> r.size().name()).setHeader("Розмір").setAutoWidth(true);
        output.addColumn(r -> Fmt.qty(r.size().cut())).setHeader("Розкроєно").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        output.addColumn(r -> Fmt.qty(r.size().sewn())).setHeader("Пошито").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        output.addColumn(r -> Fmt.qty(r.size().sold())).setHeader("Продано").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        output.addColumn(r -> Fmt.qty(r.size().shipped())).setHeader("Інше списання").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        output.addColumn(r -> signedOrBlank(r.size().corrected())).setHeader("Інвентаризація").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        output.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        output.setAllRowsVisible(true);
        var outputHint = new Paragraph("«Продано» — забране замовленнями мінус повернене скасованими; «Інше списання» — відвантажене "
                + "не через замовлення (фотосесія, брак тощо).");
        outputHint.addClassName("secondary");

        add(new H2("Звіти складу"), new HorizontalLayout(days, coverDays), valueTitle, values, consumptionTitle, paceHint,
                noConsumption, consumption, new H3("Випуск виробництва"), outputHint, noOutput, output);
        load();
    }

    private void load() {
        report = service.get(days.getValue(), coverDays.getValue());
        valueTitle.setText("Вартість залишків зараз: " + Fmt.money(report.totalValue()));
        values.setItems(report.categories());

        consumptionTitle.setText("Витрата розхідників з " + report.from().format(DATE)
                + (report.suggestedTotal().signum() > 0 ? " · замовити на " + Fmt.money(report.suggestedTotal()) : ""));
        consumption.setItems(report.consumption());
        consumption.setVisible(!report.consumption().isEmpty());
        noConsumption.setVisible(report.consumption().isEmpty());

        output.setItems(report.output().stream()
                .flatMap(m -> m.sizes().stream().map(s -> new OutputRow(m, s, s == m.sizes().getFirst())))
                .toList());
        output.setVisible(!report.output().isEmpty());
        noOutput.setVisible(report.output().isEmpty());
    }

    /** "+2", "−3", or empty for zero. */
    private static String signedOrBlank(BigDecimal value) {
        return value.signum() == 0 ? "" : (value.signum() > 0 ? "+" : "−") + Fmt.qty(value.abs());
    }
}
