package ua.newerp.web.production;

import java.util.List;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.production.CuttingPlanDto;
import ua.newerp.application.production.CuttingPlanItemDto;
import ua.newerp.application.production.CuttingPlanModelDto;
import ua.newerp.application.production.CuttingPlanService;
import ua.newerp.application.production.CuttingPlanSizeDto;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;

/**
 * What to cut so that each model gets back to its minimum split by the size shares, plus what active orders miss, and whether
 * the consumables on stock are enough for that cut.
 */
@Route("production/cut-plan")
@PageTitle("План крою — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class CuttingPlanView extends VerticalLayout {

    /** One size of a model; the model name is shown on its first size only. */
    record Row(CuttingPlanModelDto model, CuttingPlanSizeDto size, boolean first) {
    }

    final Checkbox onlyToCut = new Checkbox("Лише моделі, які треба кроїти", true);
    final Grid<Row> sizes = new Grid<>();
    final Grid<CuttingPlanItemDto> items = new Grid<>();
    final Paragraph shortage = new Paragraph();
    private final Paragraph empty = new Paragraph("Кроїти нічого: запасу всіх моделей вистачає до мінімуму й на активні замовлення.");
    private final H3 itemsTitle = new H3("Розхідники на крій");
    private final CuttingPlanService service;
    CuttingPlanDto plan;

    public CuttingPlanView(CuttingPlanService service) {
        this.service = service;
        var hint = new Paragraph("Скільки кожного розміру розкроїти, щоб запас моделі (розкроєне й пошите) повернувся до мінімуму, "
                + "розподіленого за частками розмірів, і ще вистачило на те, чого бракує активним замовленням. Мінімум і частки "
                + "задаються в картці моделі.");
        hint.addClassName("secondary");

        sizes.addComponentColumn(r -> r.first() ? new Anchor(ProductionUi.model(r.model().productionId()), r.model().name()) : new Span())
                .setHeader("Модель").setAutoWidth(true);
        sizes.addColumn(r -> r.size().name()).setHeader("Розмір").setAutoWidth(true);
        sizes.addColumn(r -> Fmt.qty(r.size().percent()) + " %").setHeader("Частка").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sizes.addColumn(r -> r.size().target()).setHeader("Мінімум").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sizes.addColumn(r -> Fmt.qty(r.size().ordered())).setHeader("Бракує замовленням").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        sizes.addColumn(r -> Fmt.qty(r.size().cutStock())).setHeader("Розкроєно").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sizes.addColumn(r -> Fmt.qty(r.size().sewnStock())).setHeader("Пошито").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sizes.addComponentColumn(r -> {
            var toCut = new Span(Fmt.qty(r.size().toCut()));
            toCut.addClassName(LumoUtility.FontWeight.BOLD);
            return toCut;
        }).setHeader("Розкроїти").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sizes.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        sizes.setAllRowsVisible(true);

        items.addColumn(CuttingPlanItemDto::name).setHeader("Розхідник").setFlexGrow(1);
        items.addColumn(i -> Fmt.qty(i.need()) + " " + i.unit()).setHeader("Потрібно").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        items.addColumn(i -> Fmt.qty(i.stock())).setHeader("На складі").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        items.addComponentColumn(i -> {
            var missing = new Span(i.shortage().signum() > 0 ? Fmt.qty(i.shortage()) : "—");
            if (i.shortage().signum() > 0) {
                missing.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.FontWeight.BOLD);
            }
            return missing;
        }).setHeader("Бракує").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        items.addColumn(i -> String.join(", ", i.models())).setHeader("Для моделей").setFlexGrow(1);
        items.addThemeVariants(GridVariant.LUMO_COMPACT);
        items.setAllRowsVisible(true);
        shortage.addClassName(LumoUtility.TextColor.ERROR);

        onlyToCut.addValueChangeListener(e -> load());
        add(new H2("План крою"), hint, onlyToCut, sizes, empty, itemsTitle, shortage, items);
        load();
    }

    private void load() {
        plan = service.get(onlyToCut.getValue());
        sizes.setItems(plan.models().stream()
                .flatMap(m -> m.sizes().stream().map(s -> new Row(m, s, s == m.sizes().getFirst())))
                .toList());
        sizes.setVisible(!plan.models().isEmpty());
        empty.setVisible(plan.models().isEmpty());

        items.setItems(plan.items());
        boolean hasItems = !plan.items().isEmpty();
        items.setVisible(hasItems);
        itemsTitle.setVisible(hasItems);
        List<CuttingPlanItemDto> lacking = plan.items().stream().filter(i -> i.shortage().signum() > 0).toList();
        shortage.removeAll();
        if (!lacking.isEmpty()) {
            shortage.add("На весь крій не вистачить розхідників: " + lacking.size() + ". Дозамовте їх на сторінці ");
            shortage.add(new Anchor("stock/reorder", "«Що дозамовити»"));
            shortage.add(" або кроїть частину.");
        }
        shortage.setVisible(!lacking.isEmpty());
    }
}
