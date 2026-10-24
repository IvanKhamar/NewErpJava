package ua.newerp.web.competitors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;

import ua.newerp.application.competitors.CompetitorLinkDto;
import ua.newerp.application.competitors.CompetitorPriceDto;
import ua.newerp.application.competitors.CompetitorPriceRequest;
import ua.newerp.application.competitors.CompetitorService;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/** One competitor page: its price history and a price typed in by hand, for sites that do not let the check read it. */
class LinkDialog extends Dialog {

    final BigDecimalField price = new BigDecimalField("Ціна, ₴");
    final Button save = new Button("Записати ціну");
    final Grid<CompetitorPriceDto> history = new Grid<>();

    private final CompetitorService service;
    private final CompetitorLinkDto link;
    private final Runnable onSaved;

    LinkDialog(CompetitorService service, CompetitorLinkDto link, Runnable onSaved) {
        this.service = service;
        this.link = link;
        this.onSaved = onSaved;
        setHeaderTitle(link.productName() + " — " + link.competitorName());
        setWidth("min(36rem, 100%)");

        var page = new Anchor(link.url(), link.url());
        page.setTarget("_blank");
        price.setLocale(Fmt.UK);
        price.setWidth("10rem");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        var form = new HorizontalLayout(price, save);
        form.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        history.addColumn(p -> Fmt.date(p.checkedAt())).setHeader("Коли").setAutoWidth(true);
        history.addColumn(p -> Fmt.money(p.price())).setHeader("Ціна").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        history.addColumn(p -> "manual".equals(p.source()) ? "вручну" : "перевірка").setHeader("Звідки").setFlexGrow(1);
        history.addThemeVariants(GridVariant.LUMO_COMPACT);
        history.setEmptyStateText("Цін ще немає.");
        history.setAllRowsVisible(true);
        history.setItems(service.history(link.id()));

        var hint = new Paragraph("Рядок в історії з'являється, коли ціна змінилась.");
        hint.addClassName("secondary");
        add(page, form, hint, history);
        getFooter().add(new Button("Закрити", e -> close()));
    }

    void save() {
        boolean saved = Actions.run("Ціну записано.", () -> service.setPrice(link.id(), new CompetitorPriceRequest(price.getValue())));
        if (saved) {
            close();
            onSaved.run();
        }
    }
}
