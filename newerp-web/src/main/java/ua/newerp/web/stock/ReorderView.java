package ua.newerp.web.stock;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.stock.PurchaseOrderService;
import ua.newerp.application.stock.ReorderLineDto;
import ua.newerp.application.stock.ReorderReportDto;
import ua.newerp.application.stock.ReorderService;
import ua.newerp.application.stock.SupplierReorderDto;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Clipboard;
import ua.newerp.web.ui.Panel;

/** What to reorder, grouped by supplier (Reorder.razor): a text to copy into a messenger, or a draft order in one click. */
@Route("stock/reorder")
@PageTitle("Що дозамовити — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class ReorderView extends VerticalLayout {

    final ReorderReportDto report;
    private final PurchaseOrderService purchaseOrders;
    private final CurrentSession session;

    public ReorderView(ReorderService reorder, PurchaseOrderService purchaseOrders, CurrentSession session) {
        this.purchaseOrders = purchaseOrders;
        this.session = session;
        report = reorder.getReport();
        setDefaultHorizontalComponentAlignment(Alignment.STRETCH); // the supplier panels take the full width

        var title = new H2("Що дозамовити");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, new H3("Разом: " + Fmt.money(report.total())));
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        var hint = new Paragraph("Позиції нижче мінімуму. Пропонуємо довести залишок до двох мінімумів; "
                + "те, що вже замовлено й ще не приїхало, віднімається.");
        hint.addClassName("secondary");
        add(header, hint);

        if (report.suppliers().isEmpty()) {
            var ok = new Paragraph("Усього вистачає — дозамовляти нічого.");
            ok.addClassNames("note", "tone-1");
            add(ok);
        }
        report.suppliers().forEach(group -> add(group(group)));
    }

    void createOrder(int supplierId) {
        int userId = session.user().orElseThrow().userId();
        Actions.run(null, () -> {
            int id = purchaseOrders.createFromReorder(supplierId, userId);
            getUI().ifPresent(ui -> ui.navigate("stock/purchase-orders/" + id));
        });
    }

    private Component group(SupplierReorderDto group) {
        var contacts = Stream.of(group.phone(), group.email()).filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(" · "));
        var grid = new Grid<ReorderLineDto>();
        grid.addComponentColumn(l -> new RouterLink(l.name(), StockItemView.class, new RouteParameters("id", String.valueOf(l.itemId()))))
                .setHeader("Позиція").setFlexGrow(1);
        grid.addColumn(l -> Fmt.qty(l.quantity()) + " " + l.unit()).setHeader("Є").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(l -> Fmt.qty(l.minQuantity())).setHeader("Мінімум").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(l -> l.onOrder().signum() > 0 ? Fmt.qty(l.onOrder()) : "").setHeader("Вже замовлено")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(l -> Fmt.qty(l.suggested()) + " " + l.unit()).setHeader("Замовити").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        grid.addColumn(l -> l.sum().signum() > 0 ? Fmt.money(l.sum()) : "").setHeader("Сума").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setItems(group.lines());
        grid.setAllRowsVisible(true);

        var copy = new Button("Скопіювати текст", VaadinIcon.COPY.create(),
                e -> Clipboard.copy(group.messageText(), "Текст скопійовано — можна вставити в месенджер."));
        var actions = new HorizontalLayout(copy);
        actions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        if (group.supplierId() != null) {
            var create = new Button("Створити замовлення", VaadinIcon.FILE_ADD.create(), e -> createOrder(group.supplierId()));
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(create);
        } else {
            var note = new Paragraph("Вкажіть постачальника в картці позиції («Змінити»), і її можна буде замовити звідси одним кліком.");
            note.addClassName("secondary");
            actions.add(note);
        }

        var panel = new Panel(group.supplierName() + " · " + Fmt.money(group.total()));
        if (!contacts.isEmpty()) {
            panel.add(new Paragraph(contacts));
        }
        panel.add(grid, actions);
        return panel;
    }
}
