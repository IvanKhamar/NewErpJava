package ua.newerp.web.stock;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.stock.PurchaseOrderFilter;
import ua.newerp.application.stock.PurchaseOrderRepository;
import ua.newerp.application.stock.PurchaseOrderSummaryDto;
import ua.newerp.domain.stock.PurchaseOrder;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;

/** Orders to suppliers (PurchaseOrders.razor). By default the open ones: drafts and sent orders, the ones that need work. */
@Route("stock/purchase-orders")
@PageTitle("Замовлення постачальникам — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class PurchaseOrderListView extends VerticalLayout {

    static final String OPEN = "open";
    static final String ALL = "all";

    final Select<String> status = new Select<>();
    final Grid<PurchaseOrderSummaryDto> grid = new Grid<>(PurchaseOrderSummaryDto.class, false);
    private final List<PurchaseOrderSummaryDto> orders;

    public PurchaseOrderListView(PurchaseOrderRepository repository) {
        setSizeFull();
        var title = new H2("Замовлення постачальникам");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var reorder = new Button("Що дозамовити", VaadinIcon.LIST.create(),
                e -> getUI().ifPresent(ui -> ui.navigate(ReorderView.class)));
        var header = new HorizontalLayout(title, reorder);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var statuses = new ArrayList<String>();
        statuses.add(OPEN);
        statuses.addAll(PurchaseStatus.ALL);
        statuses.add(ALL);
        status.setLabel("Статус");
        status.setItems(statuses);
        status.setItemLabelGenerator(s -> switch (s) {
            case OPEN -> "Відкриті";
            case ALL -> "Усі";
            default -> PurchaseStatus.label(s);
        });
        status.setValue(OPEN);
        status.addValueChangeListener(e -> applyFilter());

        grid.addColumn(PurchaseOrderSummaryDto::id).setHeader("№").setAutoWidth(true);
        grid.addColumn(PurchaseOrderSummaryDto::supplierName).setHeader("Постачальник").setFlexGrow(1);
        grid.addComponentColumn(o -> PurchaseStatus.badge(o.status())).setHeader("Статус").setAutoWidth(true);
        grid.addColumn(o -> Fmt.date(o.createdAt())).setHeader("Створено").setAutoWidth(true);
        grid.addColumn(PurchaseOrderSummaryDto::lineCount).setHeader("Позицій").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(o -> Fmt.money(o.total())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setEmptyStateText("Замовлень немає.");
        grid.setSizeFull();
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate("stock/purchase-orders/" + e.getItem().id())));

        orders = repository.list(new PurchaseOrderFilter(null));
        add(header, status, grid);
        setFlexGrow(1, grid);
        applyFilter();
    }

    List<PurchaseOrderSummaryDto> filtered() {
        String value = status.getValue();
        return orders.stream().filter(o -> switch (value) {
            case ALL -> true;
            case OPEN -> PurchaseOrder.DRAFT.equals(o.status()) || PurchaseOrder.ORDERED.equals(o.status());
            default -> value.equals(o.status());
        }).toList();
    }

    private void applyFilter() {
        grid.setItems(filtered());
    }
}
