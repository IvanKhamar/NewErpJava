package ua.newerp.web.production;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.production.ComponentDto;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.SaveComponentRequest;
import ua.newerp.application.production.SewnBomLineDto;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;

/** The model's cut components with their cutting BOMs, and the sewing BOM (ModelSpecifications.razor). */
class SpecificationsPanel extends Div {

    final TextField newComponent = new TextField("Нова деталь (перед, спинка, рукав...)");
    final Button addComponent = new Button("Додати");
    final Button editSewnBom = new Button("Змінити", VaadinIcon.EDIT.create());

    private final ProductionService service;
    private final ProductionModelDto model;
    private final Supplier<List<StockItemDto>> items;
    private final Runnable onChanged;

    /** items: the consumables for the BOM dialogs, loaded only when one opens. */
    SpecificationsPanel(ProductionService service, ProductionModelDto model, Supplier<List<StockItemDto>> items, Runnable onChanged) {
        this.service = service;
        this.model = model;
        this.items = items;
        this.onChanged = onChanged;
        if (ProductionModel.READY_MADE.equals(model.type())) {
            var note = new Paragraph("Готовий товар не кроять і не шиють, тож специфікації для нього не потрібні.");
            note.addClassNames("note", "tone-0");
            add(note);
            return;
        }

        add(new H3("Деталі крою"), secondary("«Покроєно» додає кожну деталь у запас і списує тканину за її специфікацією крою. "
                + "Рядок без розміру діє для всіх розмірів, рядок із розміром його перекриває."));
        var cards = new Div();
        cards.addClassName("two-columns");
        model.components().forEach(c -> cards.add(component(c)));
        newComponent.setMaxLength(100);
        newComponent.setMinWidth("18rem");
        addComponent.addClickListener(e -> addComponent());
        var add = new HorizontalLayout(newComponent, addComponent);
        add.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        add(cards, add);

        editSewnBom.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        editSewnBom.addClickListener(e -> new BomDialog(service, model, null, items.get(), onChanged).open());
        var title = new H3("Специфікація пошиву");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, editSewnBom);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        add(header, secondary("На один пошитий виріб: деталі крою із запасу й розхідники напряму (нитки, бірки, пакет)."), sewnBom());
    }

    void addComponent() {
        Actions.run(null, () -> {
            service.addComponent(model.id(), new SaveComponentRequest(newComponent.getValue(), model.components().size() + 1));
            onChanged.run();
        });
    }

    private Component component(ComponentDto component) {
        var rename = new Button(VaadinIcon.EDIT.create(), e -> rename(component));
        rename.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        rename.setAriaLabel("Перейменувати");
        var delete = new Button(VaadinIcon.TRASH.create(), e -> Confirm.ask("Видалити деталь «" + component.name() + "»?",
                "Разом з нею зникне її специфікація крою й рядки в специфікації пошиву.", "Видалити", "Ні", () -> Actions.run(null, () -> {
                    service.deleteComponent(component.id());
                    onChanged.run();
                })));
        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        delete.setAriaLabel("Видалити");
        var name = new H4(component.name());
        name.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(name, rename, delete);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var stock = new FlexLayout();
        stock.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        stock.addClassName(LumoUtility.Gap.XSMALL);
        model.sizes().forEach(s -> {
            var chip = new Span(s.name() + ": " + Fmt.qty(component.stock().getOrDefault(s.id(), BigDecimal.ZERO)));
            chip.addClassName("status-badge");
            stock.add(chip);
        });

        var card = new Div(header, secondary("Розкроєно в запасі:"), stock);
        card.addClassName("panel");
        if (component.bom().isEmpty()) {
            var warning = new Paragraph("Специфікацію крою не задано — тканина не списуватиметься.");
            warning.addClassName(LumoUtility.TextColor.WARNING);
            card.add(warning);
        } else {
            component.bom().forEach(line -> card.add(new Paragraph(sizeName(line.sizeId()) + ": " + line.itemName() + " — "
                    + Fmt.qty(line.qtyPerUnit()) + " " + line.unit())));
        }
        var bom = new Button("Специфікація крою", VaadinIcon.LIST.create(),
                e -> new BomDialog(service, model, component, items.get(), onChanged).open());
        bom.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        card.add(bom);
        return card;
    }

    private void rename(ComponentDto component) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Перейменувати деталь");
        var name = new TextField("Назва", component.name(), "");
        name.setMaxLength(100);
        name.setWidthFull();
        dialog.add(name);
        var save = new Button("Зберегти", e -> Actions.run(null, () -> {
            service.updateComponent(component.id(), new SaveComponentRequest(name.getValue(), component.sortOrder()));
            dialog.close();
            onChanged.run();
        }));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Скасувати", e -> dialog.close()), save);
        dialog.open();
    }

    private Component sewnBom() {
        var grid = new Grid<SewnBomLineDto>();
        grid.addColumn(l -> sizeName(l.sizeId())).setHeader("Розмір").setAutoWidth(true);
        grid.addColumn(l -> l.componentName() != null ? "Деталь: " + l.componentName() : l.itemName()).setHeader("Що").setFlexGrow(1);
        grid.addColumn(l -> Fmt.qty(l.qtyPerUnit()) + " " + (l.componentId() != null ? "шт" : l.unit())).setHeader("На виріб")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setEmptyStateText("Не задано — при пошиві нічого не списуватиметься.");
        grid.setItems(model.sewnBom());
        grid.setAllRowsVisible(true);
        grid.setMaxWidth("45rem");
        return grid;
    }

    private String sizeName(Integer sizeId) {
        if (sizeId == null) {
            return "Усі розміри";
        }
        return model.sizes().stream().filter(s -> s.id() == sizeId).map(s -> s.name()).findFirst().orElse("розмір " + sizeId);
    }

    private static Paragraph secondary(String text) {
        var paragraph = new Paragraph(text);
        paragraph.addClassName("secondary");
        return paragraph;
    }
}
