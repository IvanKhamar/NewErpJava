package ua.newerp.web.production;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.production.ProductionModelFilter;
import ua.newerp.application.production.ProductionModelSummaryDto;
import ua.newerp.application.production.ProductionRepository;
import ua.newerp.application.production.ProductionService;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/** Production models (Models.razor). There are few of them, so the filters work in memory. */
@Route("production")
@PageTitle("Моделі — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class ModelListView extends VerticalLayout {

    final TextField search = new TextField();
    final Select<String> type = new Select<>();
    final Checkbox belowMinimum = new Checkbox("Лише ті, що треба шити");
    final Grid<ProductionModelSummaryDto> grid = new Grid<>(ProductionModelSummaryDto.class, false);
    final Button sync = new Button("Передати залишки на сайт", VaadinIcon.SHOP.create());
    final Button create = new Button("Нова модель", VaadinIcon.PLUS.create());

    private final ProductionService service;
    private final List<ProductionModelSummaryDto> models;

    public ModelListView(ProductionRepository repository, ProductionService service) {
        this.service = service;
        setSizeFull();
        // Sewing and shipping pass the stock on by themselves; the button is for a full check (after changes on the old site).
        sync.addClickListener(e -> sync());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(e -> new ModelDialog(service, null,
                id -> getUI().ifPresent(ui -> ui.navigate(ProductionUi.model(id)))).open());
        var title = new H2("Моделі");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, sync, create);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        search.setPlaceholder("Пошук за назвою");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        type.setLabel("Тип");
        type.setItems(ProductionModel.CLOTHING, ProductionModel.READY_MADE);
        type.setItemLabelGenerator(t -> t == null ? "Усі" : ProductionUi.typeLabel(t));
        type.setEmptySelectionAllowed(true);
        var filters = new HorizontalLayout(search, type, belowMinimum);
        filters.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        grid.addColumn(ProductionModelSummaryDto::name).setHeader("Модель").setFlexGrow(1);
        grid.addColumn(m -> ProductionUi.typeLabel(m.type())).setHeader("Тип").setAutoWidth(true);
        grid.addColumn(m -> Fmt.qty(m.totalCut())).setHeader("Розкроєно").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(m -> Fmt.qty(m.totalSewn())).setHeader("Пошито").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(ProductionModelSummaryDto::minTotal).setHeader("Мінімум").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(m -> {
            var deficit = new Span(m.deficit().signum() > 0 ? Fmt.qty(m.deficit()) : "");
            if (m.deficit().signum() > 0) {
                deficit.addClassNames("status-badge", "tone-2");
            }
            return deficit;
        }).setHeader("Бракує").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setSizeFull();
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(ProductionUi.model(e.getItem().id()))));

        search.addValueChangeListener(e -> applyFilters());
        type.addValueChangeListener(e -> applyFilters());
        belowMinimum.addValueChangeListener(e -> applyFilters());
        models = repository.list(new ProductionModelFilter(null, null));
        add(header, filters, grid);
        setFlexGrow(1, grid);
        applyFilters();
    }

    List<ProductionModelSummaryDto> filtered() {
        String text = search.getValue().strip().toLowerCase(Locale.ROOT);
        return models.stream()
                .filter(m -> text.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(text))
                .filter(m -> type.getValue() == null || type.getValue().equals(m.type()))
                .filter(m -> !belowMinimum.getValue() || m.deficit().signum() > 0)
                .toList();
    }

    void sync() {
        Actions.run(null, () -> {
            var result = service.pushStorefront();
            Messages.success("Залишки передано: моделей " + result.models() + ", товарів на сайті " + result.products() + ".");
        });
    }

    private void applyFilters() {
        grid.setItems(filtered());
        grid.setEmptyStateText(models.isEmpty() ? "Моделей ще немає." : "Нічого не знайдено за цими умовами.");
    }
}
