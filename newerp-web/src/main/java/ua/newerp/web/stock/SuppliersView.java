package ua.newerp.web.stock;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.VaadinIcon;
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
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Panel;

/**
 * Suppliers and categories of materials (Suppliers.razor). A click on a supplier opens it in the form for changes;
 * a category is renamed in place. Only what nothing refers to can be deleted.
 */
@Route("stock/suppliers")
@PageTitle("Постачальники й категорії — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class SuppliersView extends VerticalLayout {

    final Grid<StockSupplierDto> suppliers = new Grid<>(StockSupplierDto.class, false);
    final H4 formTitle = new H4();
    final TextField name = field("Назва", 150);
    final TextField phone = field("Телефон", 32);
    final TextField email = field("Email", 96);
    final TextField supplies = field("Що постачає", 255);
    final TextField notes = new TextField("Примітки");
    final Button addSupplier = new Button("Додати постачальника");
    final Button deleteSupplier = new Button("Видалити");
    final Button newSupplier = new Button("Новий");
    final Grid<LookupItem> categories = new Grid<>();
    final TextField categoryName = field("Нова категорія", 100);
    final Button addCategory = new Button("Додати");

    private final StockRepository repository;
    private final StockService service;
    /** The supplier open in the form; null: a new one. */
    StockSupplierDto editing;

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
        suppliers.addItemClickListener(e -> edit(e.getItem()));

        addSupplier.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addSupplier.addClickListener(e -> saveSupplier());
        deleteSupplier.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        deleteSupplier.addClickListener(e -> Confirm.ask("Видалити постачальника?", "«" + editing.name() + "» зникне з довідника.",
                "Видалити", "Скасувати", this::deleteSupplier));
        newSupplier.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        newSupplier.addClickListener(e -> edit(null));
        var form = new FlexLayout(name, phone, email, supplies, notes);
        form.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        form.addClassName(LumoUtility.Gap.MEDIUM);
        var formPanel = new Div(formTitle, form, new HorizontalLayout(addSupplier, newSupplier, deleteSupplier));
        formPanel.addClassName("panel");

        categories.addComponentColumn(this::categoryField).setHeader("Назва").setFlexGrow(1);
        categories.addComponentColumn(c -> {
            var delete = new Button(VaadinIcon.TRASH.create(), e -> Confirm.ask("Видалити категорію?",
                    "«" + c.name() + "» можна видалити, лише якщо в ній немає позицій.", "Видалити", "Скасувати",
                    () -> deleteCategory(c)));
            delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            delete.setAriaLabel("Видалити категорію");
            return delete;
        }).setAutoWidth(true).setFlexGrow(0);
        categories.addThemeVariants(GridVariant.LUMO_COMPACT);
        categories.setEmptyStateText("Категорій ще немає.");
        categories.setAllRowsVisible(true);
        addCategory.addClickListener(e -> addCategory());
        var newCategory = new HorizontalLayout(categoryName, addCategory);
        newCategory.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        var left = new Div(new Panel("Постачальники", suppliers), formPanel);
        var columns = new Div(left, new Div(new Panel("Категорії розхідників", categories, newCategory)));
        columns.addClassName("two-columns");
        add(new H2("Постачальники й категорії"), columns);
        edit(null);
        load();
    }

    void edit(StockSupplierDto supplier) {
        editing = supplier;
        formTitle.setText(supplier == null ? "Новий постачальник" : "Постачальник: " + supplier.name());
        addSupplier.setText(supplier == null ? "Додати постачальника" : "Зберегти");
        deleteSupplier.setVisible(supplier != null);
        newSupplier.setVisible(supplier != null);
        name.setValue(supplier == null ? "" : supplier.name());
        phone.setValue(supplier == null ? "" : supplier.phone());
        email.setValue(supplier == null ? "" : supplier.email());
        supplies.setValue(supplier == null ? "" : supplier.categories());
        notes.setValue(supplier == null ? "" : supplier.notes());
    }

    void saveSupplier() {
        var request = new CreateStockSupplierRequest(name.getValue(), phone.getValue(), email.getValue(), supplies.getValue(),
                notes.getValue());
        boolean isNew = editing == null;
        Actions.run("Постачальника «" + name.getValue() + "» " + (isNew ? "додано." : "збережено."), () -> {
            if (isNew) {
                service.createSupplier(request);
            } else {
                service.updateSupplier(editing.id(), request);
            }
            edit(null);
            load();
        });
    }

    void deleteSupplier() {
        Actions.run("Постачальника «" + editing.name() + "» видалено.", () -> {
            service.deleteSupplier(editing.id());
            edit(null);
            load();
        });
    }

    void addCategory() {
        Actions.run(null, () -> {
            service.createCategory(new CreateStockCategoryRequest(categoryName.getValue(), null));
            categoryName.clear();
            load();
        });
    }

    void renameCategory(LookupItem category, String newName) {
        if (newName.strip().equals(category.name())) {
            return;
        }
        Actions.run("Категорію перейменовано.", () -> {
            service.updateCategory(category.id(), new CreateStockCategoryRequest(newName, null));
            load();
        });
    }

    void deleteCategory(LookupItem category) {
        Actions.run("Категорію «" + category.name() + "» видалено.", () -> {
            service.deleteCategory(category.id());
            load();
        });
    }

    /** The name is edited in place: the change is saved when the field loses focus or on Enter. */
    private TextField categoryField(LookupItem category) {
        var field = new TextField();
        field.setValue(category.name());
        field.setMaxLength(100);
        field.setWidthFull();
        field.setAriaLabel("Назва категорії");
        field.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                renameCategory(category, e.getValue());
            }
        });
        return field;
    }

    private void load() {
        suppliers.setItems(repository.listSuppliers());
        categories.setItems(repository.listCategories());
    }

    private static TextField field(String label, int maxLength) {
        var field = new TextField(label);
        field.setMaxLength(maxLength);
        return field;
    }
}
