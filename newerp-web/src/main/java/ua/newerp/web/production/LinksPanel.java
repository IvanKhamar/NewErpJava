package ua.newerp.web.production;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.production.CategoryLinkRequest;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.SetProductionLinksRequest;
import ua.newerp.application.production.SizeOptionRequest;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/**
 * Links of the model to shop products (ModelLinks.razor): directly by product id or through a category with a name filter,
 * and the model's sizes to values of the "Size" option. Writing off sewn pieces for orders depends on them.
 */
class LinksPanel extends Div {

    final TextField productIds = new TextField("Id товарів через кому");
    final List<CategoryRow> categories = new ArrayList<>();
    final Map<Integer, IntegerField> options = new LinkedHashMap<>();
    final Button save = new Button("Зберегти прив'язку");

    private final Div categoryRows = new Div();
    private final ProductionService service;
    private final ProductionModelDto model;
    private final Runnable onChanged;

    LinksPanel(ProductionService service, ProductionModelDto model, Runnable onChanged) {
        this.service = service;
        this.model = model;
        this.onChanged = onChanged;
        var hint = new Paragraph("Коли замовлення переходить у статуси списання, система шукає модель для кожного товару (спершу пряма "
                + "прив'язка, потім категорія) і розмір — за опцією «Розмір» у замовленні. Назв товарів і опцій NewErp поки не знає, "
                + "тож тут вказуються їхні id зі старого сайту.");
        hint.addClassName("secondary");

        productIds.setPlaceholder("101, 102");
        productIds.setValue(model.productIds().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        productIds.setMinWidth("18rem");
        model.categories().forEach(c -> addCategory(c.categoryId(), c.nameFilter(), c.matchExact()));
        var left = new Div(new H4("Товари напряму"), productIds, new H4("Через категорії"), categoryRows,
                new Button("Додати категорію", VaadinIcon.PLUS.create(), e -> addCategory(null, "", false)));

        var right = new Div(new H4("Розміри → опція «Розмір» (option_value_id)"));
        model.sizes().forEach(s -> {
            var field = new IntegerField(s.name());
            field.setMin(1);
            field.setValue(s.optionValueId());
            field.setClearButtonVisible(true);
            options.put(s.id(), field);
            right.add(new Div(field));
        });

        var columns = new Div(left, right);
        columns.addClassName("two-columns");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        add(hint, columns, save);
    }

    void addCategory(Integer categoryId, String nameFilter, boolean exact) {
        var row = new CategoryRow(categoryId, nameFilter, exact);
        categories.add(row);
        categoryRows.add(row.layout);
    }

    void save() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String part : productIds.getValue().split("[,; ]+")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                ids.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                Messages.error("«" + part + "» не схоже на id товару.");
                return;
            }
        }
        var request = new SetProductionLinksRequest(List.copyOf(ids),
                categories.stream().map(c -> new CategoryLinkRequest(c.id.getValue() == null ? 0 : c.id.getValue(),
                        c.nameFilter.getValue(), c.exact.getValue())).toList(),
                options.entrySet().stream().map(o -> new SizeOptionRequest(o.getKey(), o.getValue().getValue())).toList());
        Actions.run("Прив'язку збережено.", () -> {
            service.setLinks(model.id(), request);
            onChanged.run();
        });
    }

    /** A category link being edited. */
    final class CategoryRow {
        final IntegerField id = new IntegerField("Id категорії");
        final TextField nameFilter = new TextField("Назва містить");
        final Checkbox exact = new Checkbox("точно");
        final HorizontalLayout layout = new HorizontalLayout();

        CategoryRow(Integer categoryId, String filter, boolean matchExact) {
            id.setMin(1);
            id.setValue(categoryId);
            id.setWidth("8rem");
            nameFilter.setValue(filter == null ? "" : filter);
            nameFilter.setPlaceholder("порожньо — уся категорія");
            exact.setValue(matchExact);
            var remove = new Button(VaadinIcon.TRASH.create(), e -> {
                categories.remove(this);
                categoryRows.remove(layout);
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            remove.setAriaLabel("Прибрати категорію");
            layout.add(id, nameFilter, exact, remove);
            layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        }
    }
}
