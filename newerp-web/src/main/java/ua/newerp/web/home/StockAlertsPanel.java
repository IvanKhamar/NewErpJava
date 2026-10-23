package ua.newerp.web.home;

import java.util.List;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.stock.StockAlertItemDto;
import ua.newerp.application.stock.StockAlertModelDto;
import ua.newerp.application.stock.StockAlertsDto;
import ua.newerp.web.Fmt;

/** The storekeeper's "needs attention" block on the home page: consumables and models below their minimum. */
class StockAlertsPanel extends Div {

    /** Longer lists are cut; the full ones are on the reorder page and the cutting plan. */
    static final int SHOWN = 8;

    StockAlertsPanel(StockAlertsDto alerts) {
        addClassName("panel");
        add(new H3("Потребує уваги"));
        if (alerts.isEmpty()) {
            var fine = new Paragraph("Усе в нормі: розхідників і пошитих моделей не менше за мінімум.");
            fine.addClassName("secondary");
            add(fine);
            return;
        }
        if (!alerts.items().isEmpty()) {
            String title = "Розхідники нижче мінімуму: " + alerts.items().size()
                    + (alerts.notOrdered() > 0 ? ", не замовлено " + alerts.notOrdered() : ", усі вже замовлені");
            add(section(title, alerts.items().stream().limit(SHOWN).map(StockAlertsPanel::item).toList(),
                    new Anchor("stock/reorder", "Що дозамовити →")));
        }
        if (!alerts.models().isEmpty()) {
            add(section("Моделі, пошитих менше за мінімум: " + alerts.models().size(),
                    alerts.models().stream().limit(SHOWN).map(StockAlertsPanel::model).toList(),
                    new Anchor("production/cut-plan", "План крою →")));
        }
    }

    private static Div section(String title, List<ListItem> rows, Anchor more) {
        var heading = new Paragraph(title);
        heading.addClassName(LumoUtility.FontWeight.SEMIBOLD);
        var list = new UnorderedList();
        rows.forEach(list::add);
        return new Div(heading, list, more);
    }

    private static ListItem item(StockAlertItemDto i) {
        var row = new ListItem(new Anchor("stock/items/" + i.itemId(), i.name()),
                new Span(": " + Fmt.qty(i.quantity()) + " з мінімуму " + Fmt.qty(i.minQuantity()) + " " + i.unit()));
        if (i.onOrder().signum() > 0) {
            var ordered = new Span("замовлено " + Fmt.qty(i.onOrder()));
            ordered.addClassNames("status-badge", "tone-1");
            ordered.getStyle().set("margin-left", "var(--lumo-space-s)");
            row.add(ordered);
        }
        return row;
    }

    private static ListItem model(StockAlertModelDto m) {
        return new ListItem(new Anchor("production/models/" + m.productionId(), m.name()),
                new Span(": пошито " + Fmt.qty(m.sewn()) + " з " + m.minTotal() + ", бракує " + Fmt.qty(m.deficit())));
    }
}
