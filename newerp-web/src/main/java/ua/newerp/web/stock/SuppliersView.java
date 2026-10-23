package ua.newerp.web.stock;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.stock.CreateStockCategoryRequest;
import ua.newerp.application.stock.CreateStockSupplierRequest;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StockService;
import ua.newerp.application.stock.StockSupplierDto;
import ua.newerp.web.Access;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Panel;

/** Suppliers and categories of materials (Suppliers.razor): the lists and a short form to add one. */
@Route("stock/suppliers")
@PageTitle("Постачальники й категорії — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class SuppliersView extends VerticalLayout {

    final Grid<StockSupplierDto> suppliers = new Grid<>(StockSupplierDto.class, false);
    final TextField name = field("Назва", 150);
    final TextField phone = field("Телефон", 32);
    final TextField email = field("Email", 96);
    final TextField supplies = field("Що постачає", 255);
    final TextField notes = new TextField("Примітки");
    final Button addSupplier = new Button("Додати постачальника");
    final FlexLayout categories = new FlexLayout();
    final TextField categoryName = field("Нова категорія", 100);
    final Button addCategory = new Button("Додати");

    private final StockRepository repository;
    private final StockService service;

    public SuppliersView(StockRepository repository, StockService service) {
        this.repository = repository;
        this.service = service;

        suppliers.addColumn(StockSupplierDto::name).setHeader("Назва").setAutoWidth(true);
        suppliers.addColumn(StockSupplierDto::phone).setHeader("Телефон").setAutoWidth(true);
        suppliers.addColumn(StockSupplierDto::email).setHeader("Email").setAutoWidth(true);
        suppliers.addColumn(StockSupplierDto::categories).setHeader("Що постачає").setFlexGrow(1);
        suppliers.addColumn(StockSupplierDto::notes).setHeader("Примітки").setFlexGrow(1);
        suppliers.addThemeVariants(GridVariant.LUMO_COMPACT);
        suppliers.setEmptyStateText("Постачальників ще немає.");
        suppliers.setAllRowsVisible(true);

        addSupplier.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addSupplier.addClickListener(e -> addSupplier());
        var form = new FlexLayout(name, phone, email, supplies, notes);
        form.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        form.addClassName(LumoUtility.Gap.MEDIUM);

        categories.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        categories.addClassName(LumoUtility.Gap.SMALL);
        addCategory.addClickListener(e -> addCategory());
        var newCategory = new HorizontalLayout(categoryName, addCategory);
        newCategory.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        var left = new Div(new Panel("Постачальники", suppliers), new Panel("Новий постачальник", form, addSupplier));
        var columns = new Div(left, new Div(new Panel("Категорії розхідників", categories, newCategory)));
        columns.addClassName("two-columns");
        add(new H2("Постачальники й категорії"), columns);
        load();
    }

    void addSupplier() {
        var request = new CreateStockSupplierRequest(name.getValue(), phone.getValue(), email.getValue(), supplies.getValue(),
                notes.getValue());
        Actions.run("Постачальника «" + name.getValue() + "» додано.", () -> {
            service.createSupplier(request);
            for (TextField field : new TextField[] {name, phone, email, supplies, notes}) {
                field.clear();
            }
            load();
        });
    }

    void addCategory() {
        Actions.run(null, () -> {
            service.createCategory(new CreateStockCategoryRequest(categoryName.getValue(), 0));
            categoryName.clear();
            load();
        });
    }

    private void load() {
        suppliers.setItems(repository.listSuppliers());
        categories.removeAll();
        var list = repository.listCategories();
        for (LookupItem category : list) {
            var chip = new Span(category.name());
            chip.addClassName("status-badge");
            categories.add(chip);
        }
        if (list.isEmpty()) {
            categories.add(new Span("Категорій ще немає."));
        }
    }

    private static TextField field(String label, int maxLength) {
        var field = new TextField(label);
        field.setMaxLength(maxLength);
        return field;
    }
}
