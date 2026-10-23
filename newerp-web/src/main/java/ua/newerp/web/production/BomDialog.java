package ua.newerp.web.production;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;

import ua.newerp.application.production.ComponentBomRowRequest;
import ua.newerp.application.production.ComponentDto;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.ProductionSizeDto;
import ua.newerp.application.production.SetComponentBomRequest;
import ua.newerp.application.production.SetSewnBomRequest;
import ua.newerp.application.production.SewnBomRowRequest;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/**
 * A BOM editor (BomDialog.razor). With a component: its cutting BOM (a consumable per piece). Without one: the model's
 * sewing BOM (a cut component or a consumable per sewn piece). A row for a size overrides the "all sizes" row.
 */
public class BomDialog extends Dialog {

    /** What a row uses: a cut component or a consumable; in one list so both can be picked in one select. */
    record Choice(Integer componentId, Integer itemId, String label) {
    }

    final List<Row> rows = new ArrayList<>();
    final Button save = new Button("Зберегти");

    private final Div rowList = new Div();
    private final ProductionService service;
    private final ProductionModelDto model;
    private final ComponentDto component;
    private final List<Choice> choices = new ArrayList<>();
    private final Runnable onSaved;

    public BomDialog(ProductionService service, ProductionModelDto model, ComponentDto component, List<StockItemDto> items,
            Runnable onSaved) {
        this.service = service;
        this.model = model;
        this.component = component;
        this.onSaved = onSaved;
        setHeaderTitle(component == null ? "Специфікація пошиву" : "Специфікація крою: " + component.name());
        setWidth("min(48rem, 100%)");

        if (component == null) {
            model.components().forEach(c -> choices.add(new Choice(c.id(), null, "Деталь: " + c.name())));
        }
        items.forEach(i -> choices.add(new Choice(null, i.id(), i.name() + " (" + i.unit() + ")")));

        if (component != null) {
            component.bom().forEach(b -> addRow(b.sizeId(), find(null, b.itemId()), b.qtyPerUnit()));
        } else {
            model.sewnBom().forEach(b -> addRow(b.sizeId(), find(b.componentId(), b.itemId()), b.qtyPerUnit()));
        }
        var hint = new Paragraph("Кількість — " + (component == null ? "на один пошитий виріб" : "на одну деталь")
                + ". Рядок для конкретного розміру перекриває рядок «Усі розміри» з тим самим розхідником чи деталлю.");
        hint.addClassName("secondary");
        add(rowList, new Button("Додати рядок", VaadinIcon.PLUS.create(), e -> addRow(null, null, BigDecimal.ONE)), hint);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void addRow(Integer sizeId, Choice what, BigDecimal qty) {
        var row = new Row(sizeId, what, qty);
        rows.add(row);
        rowList.add(row.layout);
    }

    List<Choice> choices() {
        return choices;
    }

    void save() {
        var filled = rows.stream().filter(r -> r.what.getValue() != null).toList();
        Actions.run("Специфікацію збережено.", () -> {
            if (component != null) {
                service.setComponentBom(component.id(), new SetComponentBomRequest(filled.stream()
                        .map(r -> new ComponentBomRowRequest(r.sizeId(), r.what.getValue().itemId(), r.qty.getValue())).toList()));
            } else {
                service.setSewnBom(model.id(), new SetSewnBomRequest(filled.stream()
                        .map(r -> new SewnBomRowRequest(r.sizeId(), r.what.getValue().componentId(), r.what.getValue().itemId(),
                                r.qty.getValue()))
                        .toList()));
            }
            close();
            onSaved.run();
        });
    }

    private Choice find(Integer componentId, Integer itemId) {
        return choices.stream().filter(c -> Objects.equals(c.componentId(), componentId) && Objects.equals(c.itemId(), itemId))
                .findFirst().orElse(null);
    }

    /** A BOM row being edited. The size select holds a size, or null for all sizes. */
    final class Row {
        final Select<ProductionSizeDto> size = new Select<>();
        final Select<Choice> what = new Select<>();
        final BigDecimalField qty = new BigDecimalField("Кількість");
        final HorizontalLayout layout = new HorizontalLayout();

        Row(Integer sizeId, Choice choice, BigDecimal value) {
            size.setLabel("Розмір");
            size.setItems(model.sizes());
            size.setEmptySelectionAllowed(true);
            size.setEmptySelectionCaption("Усі розміри");
            size.setItemLabelGenerator(s -> s == null ? "Усі розміри" : s.name());
            size.setValue(model.sizes().stream().filter(s -> sizeId != null && s.id() == sizeId).findFirst().orElse(null));
            size.setWidth("10rem");
            what.setLabel(component == null ? "Деталь або розхідник" : "Розхідник");
            what.setItems(choices);
            what.setItemLabelGenerator(c -> c == null ? "" : c.label());
            what.setValue(choice);
            what.setMinWidth("16rem");
            qty.setValue(Fmt.plain(value));
            qty.setWidth("8rem");
            var remove = new Button(VaadinIcon.TRASH.create(), e -> {
                rows.remove(this);
                rowList.remove(layout);
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            remove.setAriaLabel("Прибрати рядок");
            layout.add(size, what, qty, remove);
            layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        }

        Integer sizeId() {
            return size.getValue() == null ? null : size.getValue().id();
        }
    }
}
