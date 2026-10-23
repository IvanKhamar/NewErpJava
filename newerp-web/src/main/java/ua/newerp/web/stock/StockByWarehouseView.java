package ua.newerp.web.stock;

import java.util.stream.Collectors;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.stock.StockByWarehouseDto;
import ua.newerp.application.stock.StockLocationService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;

/**
 * Every material with its stock at each place: the main stock and each warehouse that holds anything. Moving and using at a
 * warehouse is done on the material's card.
 */
@Route("stock/by-warehouse")
@PageTitle("Залишки за складами — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class StockByWarehouseView extends VerticalLayout {

    final Grid<StockByWarehouseDto.Row> grid = new Grid<>();
    final StockByWarehouseDto report;

    public StockByWarehouseView(StockLocationService service) {
        report = service.byWarehouse();
        var hint = new Paragraph("Основний склад — усе, що не лежить на інших складах. Склади задаються в «Склади й пункти видачі»; "
                + "переміщення, прихід і витрата на певному складі — у картці розхідника.");
        hint.addClassName("secondary");
        var values = new Paragraph("Вартість запасу: " + report.places().stream()
                .map(p -> p.name() + " — " + Fmt.money(p.value()))
                .collect(Collectors.joining("; ")));

        grid.addComponentColumn(r -> new RouterLink(r.name(), StockItemView.class, new RouteParameters("id", String.valueOf(r.itemId()))))
                .setHeader("Розхідник").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(StockByWarehouseDto.Row::categoryName).setHeader("Категорія").setAutoWidth(true);
        grid.addColumn(r -> Fmt.qty(r.total()) + " " + r.unit()).setHeader("Разом").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        for (int i = 0; i < report.places().size(); i++) {
            int column = i; // a lambda can only use a variable that never changes, so the loop index is copied
            grid.addColumn(r -> r.quantities().get(column).signum() == 0 ? "" : Fmt.qty(r.quantities().get(column)))
                    .setHeader(report.places().get(i).name()).setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        }
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setItems(report.rows());
        grid.setAllRowsVisible(true);
        add(new H2("Залишки за складами"), hint, values, grid);
    }
}
