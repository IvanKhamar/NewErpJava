package ua.newerp.web.production;

import java.time.format.DateTimeFormatter;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.production.SewingPlanDto;
import ua.newerp.application.production.SewingPlanIssueDto;
import ua.newerp.application.production.SewingPlanRowDto;
import ua.newerp.application.production.SewingPlanService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;

/**
 * What to sew for the active orders, by model and size (SewingPlan.razor). Free sewn stock covers the need first, and cut
 * sets are sewn before new cutting.
 */
@Route("production/plan")
@PageTitle("План пошиву — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class SewingPlanView extends VerticalLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    final Checkbox onlyToSew = new Checkbox("Лише те, що треба шити", true);
    final Grid<SewingPlanRowDto> grid = new Grid<>();
    private final Paragraph hint = new Paragraph();
    private final Paragraph empty = new Paragraph();
    private final Div issues = new Div();
    private final SewingPlanService service;
    SewingPlanDto plan;

    public SewingPlanView(SewingPlanService service) {
        this.service = service;
        hint.addClassName("secondary");
        grid.addComponentColumn(r -> new Anchor(ProductionUi.model(r.productionId()), r.modelName())).setHeader("Модель")
                .setAutoWidth(true);
        grid.addColumn(SewingPlanRowDto::sizeName).setHeader("Розмір").setAutoWidth(true);
        grid.addColumn(r -> Fmt.qty(r.ordered())).setHeader("Замовлено").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(r -> Fmt.qty(r.missing())).setHeader("Бракує").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(r -> Fmt.qty(r.sewnStock())).setHeader("Пошито").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(r -> Fmt.qty(r.cutStock())).setHeader("Розкроєно").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(r -> {
            var toSew = new Span(Fmt.qty(r.toSew()));
            toSew.addClassName(LumoUtility.FontWeight.BOLD);
            return toSew;
        }).setHeader("Пошити").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(r -> Fmt.qty(r.toCut())).setHeader("Розкроїти").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(r -> {
            var deadline = new Span(r.deadline() == null ? "" : r.deadline().format(DATE));
            if (r.overdue()) {
                deadline.addClassName(LumoUtility.TextColor.ERROR);
            }
            return deadline;
        }).setHeader("Термін").setAutoWidth(true);
        grid.addComponentColumn(r -> {
            var links = new FlexLayout();
            links.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            links.addClassName(LumoUtility.Gap.SMALL);
            r.orderIds().forEach(id -> links.add(new Anchor("orders/" + id, "#" + id)));
            return links;
        }).setHeader("Замовлення").setFlexGrow(1);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);

        issues.setWidthFull();
        onlyToSew.addValueChangeListener(e -> load());
        add(new H2("План пошиву"), hint, onlyToSew, grid, empty, issues);
        load();
    }

    private void load() {
        plan = service.get(onlyToSew.getValue());
        hint.setText("Скільки кожного розміру треба пошити для активних замовлень (" + plan.orderCount() + " замовлень). Вільний пошитий "
                + "запас покриває потребу першим, розкроєне шиється раніше за новий крій. Термін — найраніша дата готовності: дата "
                + "замовлення плюс термін виготовлення категорії товару.");
        grid.setItems(plan.rows());
        grid.setVisible(!plan.rows().isEmpty());
        empty.setText(onlyToSew.getValue() ? "Шити нічого: пошитого запасу вистачає на всі активні замовлення."
                : "Активних замовлень із моделями немає.");
        empty.setVisible(plan.rows().isEmpty());

        issues.removeAll();
        if (!plan.issues().isEmpty()) {
            var list = new Grid<SewingPlanIssueDto>();
            list.addColumn(SewingPlanIssueDto::productName).setHeader("Товар").setFlexGrow(1);
            list.addColumn(SewingPlanIssueDto::reason).setHeader("Чому").setFlexGrow(1);
            list.addColumn(i -> "замовлень: " + i.orderIds().size()).setHeader("").setAutoWidth(true);
            list.addThemeVariants(GridVariant.LUMO_COMPACT);
            list.setItems(plan.issues());
            list.setAllRowsVisible(true);
            var details = new Details("Не враховано товарів: " + plan.issues().size(), list);
            details.setWidthFull();
            issues.add(details);
        }
    }
}
