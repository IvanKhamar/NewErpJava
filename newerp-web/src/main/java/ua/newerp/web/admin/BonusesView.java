package ua.newerp.web.admin;

import java.time.LocalDate;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.staff.BonusReport;
import ua.newerp.application.staff.BonusService;
import ua.newerp.application.staff.ManagerBonusRow;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/** Managers' bonuses for a period (Bonuses.razor). */
@Route("bonuses")
@PageTitle("Бонуси менеджерів — NewErp")
@RolesAllowed(Access.ADMIN)
public class BonusesView extends VerticalLayout {

    final DatePicker from = new DatePicker("З");
    final DatePicker to = new DatePicker("По");
    final Button calculate = new Button("Порахувати");
    final Span percent = new Span();
    final Span total = new Span();
    final Grid<ManagerBonusRow> grid = new Grid<>();

    private final BonusService service;

    public BonusesView(BonusService service) {
        this.service = service;
        var hint = new Paragraph("Рахуються замовлення відповідального менеджера, створені за період, у статусах, що зараховуються "
                + "(налаштування bonus у application.yml).");
        hint.addClassName("secondary");
        LocalDate today = LocalDate.now();
        from.setLocale(Fmt.UK);
        to.setLocale(Fmt.UK);
        from.setValue(today.withDayOfMonth(1));
        to.setValue(today);
        calculate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        calculate.addClickListener(e -> load());
        percent.addClassName(LumoUtility.Margin.Start.AUTO);
        total.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.SEMIBOLD);
        var filters = new HorizontalLayout(from, to, calculate, percent, total);
        filters.setWidthFull();
        filters.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        filters.addClassName("panel");

        grid.addColumn(r -> r.managerName() == null ? "користувач " + r.managerId() : r.managerName()).setHeader("Менеджер").setFlexGrow(1);
        grid.addColumn(ManagerBonusRow::ordersCount).setHeader("Замовлень").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(r -> Fmt.money(r.revenue())).setHeader("Виручка").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(r -> Fmt.money(r.bonus())).setHeader("Бонус").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("За цей період зарахованих замовлень немає.");
        grid.setAllRowsVisible(true);

        add(new H2("Бонуси менеджерів"), hint, filters, grid);
        setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        load();
    }

    void load() {
        if (from.isEmpty() || to.isEmpty()) {
            return;
        }
        Actions.run(null, () -> {
            BonusReport report = service.calculate(from.getValue(), to.getValue());
            percent.setText("Ставка: " + Fmt.qty(report.percent()) + " %");
            total.setText("Разом: " + Fmt.money(report.totalBonus()));
            grid.setItems(report.managers());
        });
    }
}
