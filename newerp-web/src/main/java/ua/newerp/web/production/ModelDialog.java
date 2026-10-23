package ua.newerp.web.production;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.SaveProductionModelRequest;
import ua.newerp.application.production.SizeRequest;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/** A new model, or changes to an existing one (ModelDialog.razor): name, type, minimum and sizes with their shares. */
public class ModelDialog extends Dialog {

    final TextField name = new TextField("Назва");
    final Select<String> type = new Select<>();
    final IntegerField minTotal = new IntegerField("Мінімум пошитих");
    final List<SizeRow> sizes = new ArrayList<>();
    final Button save;

    private final Div sizeRows = new Div();
    private final ProductionService service;
    private final ProductionModelDto model;
    private final IntConsumer onSaved;

    /** model: the model to change; null for a new one. onSaved gets the model id. */
    public ModelDialog(ProductionService service, ProductionModelDto model, IntConsumer onSaved) {
        this.service = service;
        this.model = model;
        this.onSaved = onSaved;
        setHeaderTitle(model == null ? "Нова модель" : "Змінити модель");
        setWidth("min(36rem, 100%)");

        name.setMaxLength(150);
        name.setWidthFull();
        name.setAutofocus(true);
        type.setLabel("Тип");
        type.setItems(ProductionModel.CLOTHING, ProductionModel.READY_MADE);
        type.setItemLabelGenerator(ProductionUi::typeLabel);
        minTotal.setMin(0);
        minTotal.setHelperText("Менше — модель потрапить у «треба шити»");
        add(name, new HorizontalLayout(type, minTotal), new H4("Розміри"));
        if (model != null) {
            var hint = new Paragraph("Розміри зіставляються за назвою: перейменування — це новий розмір. Розмір із залишком прибрати не можна.");
            hint.addClassName("secondary");
            add(hint);
        }
        add(sizeRows, new Button("Додати розмір", VaadinIcon.PLUS.create(), e -> addSize("", BigDecimal.ZERO)));

        if (model == null) {
            type.setValue(ProductionModel.CLOTHING);
            minTotal.setValue(30);
            for (String size : List.of("S", "M", "L", "XL")) {
                addSize(size, BigDecimal.ZERO);
            }
        } else {
            name.setValue(model.name());
            type.setValue(model.type());
            minTotal.setValue(model.minTotal());
            model.sizes().forEach(s -> addSize(s.name(), s.percent()));
        }

        save = new Button(model == null ? "Створити" : "Зберегти", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        var request = new SaveProductionModelRequest(name.getValue(),
                sizes.stream().filter(s -> !s.name.getValue().isBlank()).map(SizeRow::toRequest).toList(),
                type.getValue(), minTotal.getValue() == null ? 0 : minTotal.getValue(), model == null ? 0 : model.sortOrder());
        Actions.run(null, () -> {
            int id;
            if (model == null) {
                id = service.createModel(request);
            } else {
                id = model.id();
                service.updateModel(id, request);
            }
            close();
            onSaved.accept(id);
        });
    }

    void addSize(String sizeName, BigDecimal percent) {
        var row = new SizeRow(sizeName, percent);
        sizes.add(row);
        sizeRows.add(row.layout);
    }

    /** A size being edited: its name and share of a cut, in percent. */
    final class SizeRow {
        final TextField name = new TextField("Розмір");
        final BigDecimalField percent = new BigDecimalField("Частка, %");
        final HorizontalLayout layout = new HorizontalLayout();

        SizeRow(String sizeName, BigDecimal value) {
            name.setValue(sizeName);
            name.setMaxLength(32);
            percent.setValue(Fmt.plain(value));
            var remove = new Button(VaadinIcon.TRASH.create(), e -> {
                sizes.remove(this);
                sizeRows.remove(layout);
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            remove.setAriaLabel("Прибрати розмір");
            layout.add(name, percent, remove);
            layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        }

        SizeRequest toRequest() {
            return new SizeRequest(name.getValue().strip(), percent.getValue());
        }
    }
}
