package ua.newerp.web.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.IntConsumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.stock.SaveStockItemRequest;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockService;
import ua.newerp.application.stock.StockSupplierDto;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/** A new material or a change of one (ItemDialog.razor); the stock itself is not changed here. The item's id goes to onSaved. */
public class StockItemDialog extends Dialog {

    final TextField name = new TextField("Назва");
    final TextField unit = new TextField("Одиниця");
    final BigDecimalField minQuantity = new BigDecimalField("Мінімальний залишок");
    final Select<LookupItem> category = new Select<>();
    final Select<StockSupplierDto> supplier = new Select<>();
    final BigDecimalField price = new BigDecimalField("Ціна за одиницю, ₴");
    final TextField usedFor = new TextField("Для чого");
    final Button save;

    private final StockService service;
    private final StockItemDto item;
    private final IntConsumer onSaved;

    /** @param item null: a new material */
    public StockItemDialog(StockService service, List<LookupItem> categories, List<StockSupplierDto> suppliers, StockItemDto item,
            IntConsumer onSaved) {
        this.service = service;
        this.item = item;
        this.onSaved = onSaved;
        setHeaderTitle(item == null ? "Новий розхідник" : "Змінити розхідник");
        setWidth("min(36rem, 100%)");

        name.setMaxLength(150);
        unit.setMaxLength(16);
        unit.setPlaceholder("м, шт, кг");
        unit.setValue("шт");
        category.setLabel("Категорія");
        category.setItems(categories);
        category.setItemLabelGenerator(c -> c == null ? "—" : c.name());
        category.setEmptySelectionAllowed(true);
        supplier.setLabel("Постачальник");
        supplier.setItems(suppliers);
        supplier.setItemLabelGenerator(s -> s == null ? "—" : s.name());
        supplier.setEmptySelectionAllowed(true);
        usedFor.setMaxLength(255);
        if (item != null) {
            name.setValue(item.name());
            unit.setValue(item.unit());
            minQuantity.setValue(Fmt.plain(item.minQuantity()));
            category.setValue(categories.stream().filter(c -> item.categoryId() != null && c.id() == item.categoryId()).findFirst()
                    .orElse(null));
            supplier.setValue(suppliers.stream().filter(s -> item.supplierId() != null && s.id() == item.supplierId()).findFirst()
                    .orElse(null));
            price.setValue(Fmt.plain(item.price()));
            usedFor.setValue(item.usedFor() == null ? "" : item.usedFor());
        }
        var form = new FormLayout(name, unit, minQuantity, category, supplier, price, usedFor);
        form.setColspan(name, 2);
        form.setColspan(category, 2);
        form.setColspan(supplier, 2);
        add(form);

        save = new Button(item == null ? "Створити" : "Зберегти", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        var request = new SaveStockItemRequest(category.getValue() == null ? null : category.getValue().id(),
                supplier.getValue() == null ? null : supplier.getValue().id(), name.getValue(), unit.getValue(),
                orZero(minQuantity.getValue()), usedFor.getValue(), orZero(price.getValue()));
        Actions.run(null, () -> {
            int id;
            if (item == null) {
                id = service.createItem(request);
            } else {
                service.updateItem(item.id(), request);
                id = item.id();
            }
            close();
            onSaved.accept(id);
        });
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
