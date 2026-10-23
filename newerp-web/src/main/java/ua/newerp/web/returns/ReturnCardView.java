package ua.newerp.web.returns;

import java.util.ArrayList;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.returns.AddReturnHistoryRequest;
import ua.newerp.application.returns.ReturnDetails;
import ua.newerp.application.returns.ReturnLookupItem;
import ua.newerp.application.returns.ReturnLookupsDto;
import ua.newerp.application.returns.ReturnService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.orders.OrderCardView;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.InfoTable;
import ua.newerp.web.ui.Panel;

/** A product return (ReturnCard.razor): its data, the log, and a new status with an action and a comment. */
@Route("returns/:id(\\d+)")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class ReturnCardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private static final ReturnLookupItem NO_ACTION = new ReturnLookupItem(0, "—");

    final Select<ReturnLookupItem> status = new Select<>();
    final Select<ReturnLookupItem> action = new Select<>();
    final TextArea comment = new TextArea("Коментар");
    final Button save = new Button("Зберегти");

    private final ReturnService returns;
    private int id;
    private ReturnDetails details;

    public ReturnCardView(ReturnService returns) {
        this.returns = returns;
        ReturnLookupsDto lookups = returns.lookups();
        status.setLabel("Статус");
        status.setItems(lookups.statuses());
        status.setItemLabelGenerator(ReturnLookupItem::name);
        var actions = new ArrayList<ReturnLookupItem>();
        actions.add(NO_ACTION);
        actions.addAll(lookups.actions());
        action.setLabel("Дія");
        action.setItems(actions);
        action.setItemLabelGenerator(ReturnLookupItem::name);
        comment.setMinRows(3);
        status.setWidthFull();
        action.setWidthFull();
        comment.setWidthFull();
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        open(event.getRouteParameters().getInteger("id").orElse(0));
    }

    void open(int returnId) {
        id = returnId;
        try {
            details = returns.get(id);
        } catch (NotFoundException e) {
            removeAll();
            add(back(), new Paragraph("Повернення №" + id + " не знайдено."));
            return;
        }
        render();
    }

    @Override
    public String getPageTitle() {
        return "Повернення " + id + " — NewErp";
    }

    void save() {
        var request = new AddReturnHistoryRequest(status.getValue() == null ? details.statusId() : status.getValue().id(),
                comment.getValue(), action.getValue() == null ? 0 : action.getValue().id());
        Actions.run("Збережено.", () -> {
            returns.addHistory(id, request);
            open(id);
        });
    }

    private void render() {
        removeAll();
        var info = new InfoTable()
                .row("Замовлення", new RouterLink("#" + details.orderId(), OrderCardView.class,
                        new RouteParameters("id", String.valueOf(details.orderId()))))
                .row("Клієнт", String.join(" ", details.customer() + ",", nonNull(details.telephone()), nonNull(details.email())).strip())
                .row("Товар", details.product() + " (" + details.model() + ") × " + details.quantity()
                        + (details.opened() ? ", розпакований" : ""))
                .row("Причина", details.reasonName())
                .row("Дія", details.actionName())
                .row("Статус", details.statusName() == null ? "статус " + details.statusId() : details.statusName())
                .row("Створено", Fmt.date(details.dateAdded()));
        if (details.comment() != null && !details.comment().isBlank()) {
            var text = new Span(details.comment());
            text.addClassName("pre-line");
            info.row("Коментар", text);
        }

        var log = new UnorderedList();
        for (var entry : details.history()) {
            var when = new Span(" " + Fmt.date(entry.dateAdded()));
            when.addClassName("secondary");
            var item = new ListItem(new Span(entry.statusName() == null ? "статус " + entry.statusId() : entry.statusName()), when);
            if (entry.comment() != null && !entry.comment().isBlank()) {
                var text = new Div(new Span(entry.comment()));
                text.addClassName("pre-line");
                item.add(text);
            }
            log.add(item);
        }

        status.setValue(status.getListDataView().getItems().filter(s -> s.id() == details.statusId()).findFirst().orElse(null));
        action.setValue(action.getListDataView().getItems().filter(a -> a.id() == details.actionId()).findFirst().orElse(NO_ACTION));
        comment.clear();

        var left = new Div(new Panel("Дані", info), new Panel("Журнал", log));
        var right = new Div(new Panel("Новий статус", status, action, comment, save));
        var columns = new Div(left, right);
        columns.addClassName("two-columns");
        add(back(), new H2("Повернення №" + details.id()), columns);
    }

    private static Component back() {
        var link = new RouterLink("← Повернення", ReturnListView.class);
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
