package ua.newerp.web.production;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.production.ProductionLocationService;
import ua.newerp.application.production.ProductionLogFilter;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionRepository;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.ProductionSizeDto;
import ua.newerp.application.production.SizePlacesDto;
import ua.newerp.application.stock.StockItemFilter;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;

/** A production model (ModelCard.razor): sewn total against the minimum, stock operations, BOMs, shop links and the log. */
@Route("production/models/:id(\\d+)")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class ModelCardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    final Button edit = new Button("Змінити", VaadinIcon.EDIT.create());
    final Button deactivate = new Button("Прибрати модель");
    final Button stocktaking = new Button("Інвентаризація", VaadinIcon.CLIPBOARD_CHECK.create());
    TabSheet tabs = new TabSheet();
    OperationPanel operations;

    private final ProductionRepository repository;
    private final ProductionService service;
    private final StockRepository stock;
    private final ProductionLocationService locations;
    private final LocationService warehouses;
    private final CurrentSession session;
    private int id;
    private ProductionModelDto model;
    private List<LocationDto> warehouseList = List.of();
    private List<SizePlacesDto> places = List.of();

    public ModelCardView(ProductionRepository repository, ProductionService service, StockRepository stock,
            ProductionLocationService locations, LocationService warehouses, CurrentSession session) {
        this.repository = repository;
        this.service = service;
        this.stock = stock;
        this.locations = locations;
        this.warehouses = warehouses;
        this.session = session;
        edit.addClickListener(e -> new ModelDialog(service, model, saved -> {
            Messages.success("Зміни збережено.");
            open(id);
        }).open());
        stocktaking.addClickListener(e -> new ModelStocktakingDialog(service, model, warehouseList, places,
                session.user().orElseThrow(), done -> open(id)).open());
        deactivate.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        deactivate.addClickListener(e -> Confirm.ask("Прибрати модель?",
                "Модель зникне зі списків (як і на старому сайті, дані не видаляються).", "Прибрати", "Ні", this::deactivate));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        open(event.getRouteParameters().getInteger("id").orElse(0));
    }

    @Override
    public String getPageTitle() {
        return (model == null ? "Модель" : model.name()) + " — NewErp";
    }

    void open(int modelId) {
        List<String> warnings = operations == null || modelId != id ? List.of() : operations.lastWarnings();
        id = modelId;
        model = repository.getDto(id).orElse(null);
        removeAll();
        if (model == null) {
            add(back(), new Paragraph("Модель №" + id + " не знайдено."));
            return;
        }
        var title = new H2(model.name());
        var type = new Span(ProductionUi.typeLabel(model.type()));
        type.addClassName("status-badge");
        type.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, type, stocktaking, edit, deactivate);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        BigDecimal sewn = model.sizes().stream().map(ProductionSizeDto::sewnStock).reduce(BigDecimal.ZERO, BigDecimal::add);
        var total = new Paragraph("Пошито разом: " + Fmt.qty(sewn) + " з мінімуму " + model.minTotal());
        BigDecimal missing = BigDecimal.valueOf(model.minTotal()).subtract(sewn);
        if (missing.signum() > 0) {
            var badge = new Span("треба шити ще " + Fmt.qty(missing));
            badge.addClassNames("status-badge", "tone-2");
            badge.getStyle().set("margin-left", "var(--lumo-space-s)");
            total.add(badge);
        }

        warehouseList = warehouses.list(null);
        places = warehouseList.isEmpty() ? List.of() : locations.get(id);
        int selected = tabs.getSelectedIndex(); // a reload keeps the open tab
        tabs = new TabSheet();
        tabs.setWidthFull();
        var user = session.user().orElseThrow();
        operations = new OperationPanel(service, model, warehouseList, user, () -> open(id));
        operations.showWarnings(warnings);
        tabs.add("Залишки й операції", operations);
        if (!warehouseList.isEmpty()) {
            tabs.add("За складами", new SewnPlacesPanel(locations, id, places, warehouseList, user, () -> open(id)));
        }
        tabs.add("Специфікації", new SpecificationsPanel(service, model,
                () -> stock.listItems(new StockItemFilter(null, null)), () -> open(id)));
        tabs.add("Прив'язка до магазину", new LinksPanel(service, model, () -> open(id)));
        tabs.add("Журнал", log());
        tabs.setSelectedIndex(Math.max(selected, 0));
        add(back(), header, total, tabs);
    }

    void deactivate() {
        Actions.run(null, () -> {
            service.deactivateModel(id);
            getUI().ifPresent(ui -> ui.navigate(ModelListView.class));
        });
    }

    private Component log() {
        var grid = new ProductionLogGrid(repository.listLog(new ProductionLogFilter(id, null, 200)), false);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private static Component back() {
        var link = new RouterLink("← Моделі", ModelListView.class);
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }
}
