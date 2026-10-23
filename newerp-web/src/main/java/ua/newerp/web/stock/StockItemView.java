package ua.newerp.web.stock;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.stock.ItemPlacementDto;
import ua.newerp.application.stock.PlaceDto;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockLocationService;
import ua.newerp.application.stock.StockMovementDto;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StockService;
import ua.newerp.application.stock.StockTransferDto;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.InfoTable;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Panel;

/** A material (ItemCard.razor): the stock with its minimum, the details and the movement log. */
@Route("stock/items/:id(\\d+)")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class StockItemView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    final BigDecimalField minQuantity = new BigDecimalField();
    final Button saveMinimum = new Button("Зберегти");
    final Button edit = new Button("Змінити", VaadinIcon.EDIT.create());
    final Button adjust = new Button("Прихід або витрата", VaadinIcon.EXCHANGE.create());
    final Button transfer = new Button("Перемістити", VaadinIcon.ARROWS_LONG_H.create());
    final Button archive = new Button("Прибрати в архів");

    private final StockRepository repository;
    private final StockService service;
    private final StockLocationService locations;
    private final LocationService warehouses;
    private final CurrentSession session;
    private int id;
    private StockItemDto item;
    /** The main stock and every warehouse, with this item's quantity at each; just the main stock without warehouses. */
    List<PlaceDto> places = List.of();

    public StockItemView(StockRepository repository, StockService service, StockLocationService locations, LocationService warehouses,
            CurrentSession session) {
        this.repository = repository;
        this.service = service;
        this.locations = locations;
        this.warehouses = warehouses;
        this.session = session;
        saveMinimum.addClickListener(e -> saveMinimum());
        minQuantity.addValueChangeListener(e -> saveMinimum.setEnabled(item != null && e.getValue() != null
                && e.getValue().compareTo(item.minQuantity()) != 0));
        edit.addClickListener(e -> new StockItemDialog(service, repository.listCategories(), repository.listSuppliers(), item, saved -> {
            Messages.success("Зміни збережено.");
            open(id);
        }).open());
        archive.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        archive.addClickListener(e -> Confirm.ask("Прибрати в архів?",
                "«" + item.name() + "» зникне зі списків, журнал руху лишиться. Можна лише без залишку.", "Прибрати", "Скасувати",
                this::archive));
        adjust.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        adjust.addClickListener(e -> new AdjustStockDialog(service, locations, places, item, session.user().orElseThrow(),
                movement -> open(id)).open());
        transfer.addClickListener(e -> new TransferStockDialog(locations, places, item, session.user().orElseThrow(), () -> open(id))
                .open());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        open(event.getRouteParameters().getInteger("id").orElse(0));
    }

    @Override
    public String getPageTitle() {
        return (item == null ? "Розхідник" : item.name()) + " — NewErp";
    }

    void open(int itemId) {
        id = itemId;
        item = repository.getItemDto(id).orElse(null);
        removeAll();
        if (item == null) {
            add(back(), new Paragraph("Розхідник №" + id + " не знайдено."));
            return;
        }
        var title = new H2(item.name());
        title.addClassName(LumoUtility.Margin.End.AUTO);
        ItemPlacementDto placement = locations.get(id);
        places = places(placement, warehouses.list(null));
        transfer.setVisible(places.size() > 1);
        var header = new HorizontalLayout(title, archive, edit, transfer, adjust);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var quantity = new H3(Fmt.qty(item.quantity()) + " " + item.unit());
        if (item.isBelowMinimum()) {
            quantity.addClassName(LumoUtility.TextColor.WARNING);
        }
        var stock = new Panel("Залишок", quantity);
        if (item.isBelowMinimum()) {
            var hint = new Paragraph("Нижче мінімуму — варто дозамовити.");
            hint.addClassName(LumoUtility.TextColor.WARNING);
            stock.add(hint);
        }
        if (placement.places().size() > 1) {
            var byPlace = new InfoTable();
            placement.places().forEach(p -> byPlace.row(p.name(), Fmt.qty(p.quantity()) + " " + item.unit()));
            stock.add(byPlace);
        }
        stock.add(new InfoTable()
                .row("Категорія", item.categoryName())
                .row("Постачальник", item.supplierName())
                .row("Ціна", item.price().signum() > 0 ? Fmt.money(item.price()) : "—")
                .row("Для чого", item.usedFor()));
        minQuantity.setLabel("Мінімальний залишок, " + item.unit());
        minQuantity.setValue(Fmt.plain(item.minQuantity()));
        saveMinimum.setEnabled(false);
        var minimum = new HorizontalLayout(minQuantity, saveMinimum);
        minimum.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        stock.add(minimum);

        var log = new Div(new Panel("Журнал руху", movements(repository.listMovements(id))));
        List<StockTransferDto> moves = locations.transfers(id);
        if (!moves.isEmpty()) {
            log.add(new Panel("Переміщення між складами", transfers(moves, item.unit())));
        }
        var columns = new Div(log, new Div(stock));
        columns.addClassName("two-columns");
        add(back(), header, columns);
    }

    void archive() {
        Actions.run("«" + item.name() + "» прибрано в архів.", () -> {
            service.archiveItem(id);
            getUI().ifPresent(ui -> ui.navigate(StockListView.class));
        });
    }

    void saveMinimum() {
        var value = minQuantity.getValue();
        Actions.run("Мінімальний залишок змінено.", () -> {
            service.setMinQuantity(id, value);
            open(id);
        });
    }

    private static Component movements(List<StockMovementDto> list) {
        var grid = new Grid<StockMovementDto>();
        grid.addColumn(m -> Fmt.date(m.createdAt())).setHeader("Коли").setAutoWidth(true);
        grid.addComponentColumn(m -> {
            var delta = new Span((m.delta().signum() > 0 ? "+" : "−") + Fmt.qty(m.delta().abs()));
            delta.addClassName(m.delta().signum() > 0 ? LumoUtility.TextColor.SUCCESS : LumoUtility.TextColor.ERROR);
            return delta;
        }).setHeader("Зміна").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(m -> Fmt.qty(m.quantityAfter())).setHeader("Стало").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(StockMovementDto::reason).setHeader("Причина").setFlexGrow(1);
        grid.addColumn(StockMovementDto::username).setHeader("Хто").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setEmptyStateText("Руху ще не було.");
        grid.setItems(list);
        grid.setAllRowsVisible(true);
        return grid;
    }

    /** The main stock first, then every warehouse alphabetically (as the service sorts), also those without this item. */
    static List<PlaceDto> places(ItemPlacementDto placement, List<LocationDto> all) {
        if (all.isEmpty()) {
            return List.of(placement.places().getFirst());
        }
        Map<Integer, BigDecimal> at = placement.places().stream().filter(p -> p.warehouseId() != null)
                .collect(Collectors.toMap(PlaceDto::warehouseId, PlaceDto::quantity));
        var result = new ArrayList<PlaceDto>(all.size() + 1);
        result.add(placement.places().getFirst());
        all.stream()
                .sorted(Comparator.comparing(LocationDto::name, Collator.getInstance(Fmt.UK)))
                .forEach(w -> result.add(new PlaceDto(w.id(), w.name(), at.getOrDefault(w.id(), BigDecimal.ZERO))));
        return result;
    }

    private static Component transfers(List<StockTransferDto> list, String unit) {
        var grid = new Grid<StockTransferDto>();
        grid.addColumn(t -> Fmt.date(t.createdAt())).setHeader("Коли").setAutoWidth(true);
        grid.addColumn(t -> t.fromName() + " → " + t.toName()).setHeader("Звідки → куди").setAutoWidth(true);
        grid.addColumn(t -> Fmt.qty(t.quantity()) + " " + unit).setHeader("Скільки").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(StockTransferDto::comment).setHeader("Коментар").setFlexGrow(1);
        grid.addColumn(StockTransferDto::username).setHeader("Хто").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setItems(list);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private static Component back() {
        var link = new RouterLink("← Розхідники", StockListView.class);
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }
}
