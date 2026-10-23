package ua.newerp.web.production;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionOperation;
import ua.newerp.application.production.ProductionOperationRequest;
import ua.newerp.application.production.ProductionOperationResult;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.ProductionSizeDto;
import ua.newerp.application.production.SizeQuantity;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/**
 * A stock operation on a model for several sizes at once (OperationPanel.razor): cut, to sewing, sewn (receipt), shipped.
 * After a success onDone reloads the card; the warnings stay visible in the new panel through {@link #showWarnings}.
 */
class OperationPanel extends Div {

    final RadioButtonGroup<ProductionOperation> operation = new RadioButtonGroup<>();
    final Map<Integer, BigDecimalField> quantities = new LinkedHashMap<>();
    final TextField comment = new TextField("Коментар (необов'язково)");
    final Button submit = new Button();

    private final Paragraph hint = new Paragraph();
    private final Div warnings = new Div();
    private final ProductionService service;
    private final ProductionModelDto model;
    private final CurrentUser user;
    private final Runnable onDone;
    private List<String> lastWarnings = List.of();

    OperationPanel(ProductionService service, ProductionModelDto model, CurrentUser user, Runnable onDone) {
        this.service = service;
        this.model = model;
        this.user = user;
        this.onDone = onDone;
        addClassName("panel");
        boolean clothing = ProductionModel.CLOTHING.equals(model.type());

        operation.setItems(ProductionUi.operationsFor(model.type()));
        operation.setItemLabelGenerator(o -> ProductionUi.operationLabel(o, model.type()));
        operation.addValueChangeListener(e -> {
            hint.setText(hint(e.getValue()));
            submit.setText(ProductionUi.operationLabel(e.getValue(), model.type()));
        });
        hint.addClassName("secondary");

        for (ProductionSizeDto size : model.sizes()) {
            var field = new BigDecimalField();
            field.setWidth("9rem");
            field.setAriaLabel("Кількість, " + size.name());
            quantities.put(size.id(), field);
        }
        var grid = new Grid<ProductionSizeDto>();
        grid.addColumn(ProductionSizeDto::name).setHeader("Розмір").setFlexGrow(1);
        if (clothing) {
            grid.addColumn(s -> Fmt.qty(s.cutStock())).setHeader("Розкроєно").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        }
        grid.addColumn(s -> Fmt.qty(s.sewnStock())).setHeader("Пошито").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(s -> quantities.get(s.id())).setHeader("Кількість").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setItems(model.sizes());
        grid.setAllRowsVisible(true);

        comment.setMaxLength(255);
        comment.setMinWidth("16rem");
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.addClickListener(e -> submit());
        var actions = new HorizontalLayout(comment, submit);
        actions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        operation.setValue(ProductionUi.operationsFor(model.type()).getFirst());
        add(operation, hint, grid, actions, warnings);
    }

    void submit() {
        List<SizeQuantity> rows = model.sizes().stream()
                .filter(s -> quantities.get(s.id()).getValue() != null && quantities.get(s.id()).getValue().signum() > 0)
                .map(s -> new SizeQuantity(s.id(), quantities.get(s.id()).getValue()))
                .toList();
        if (rows.isEmpty()) {
            Messages.warning("Вкажіть кількість хоча б для одного розміру.");
            return;
        }
        ProductionOperation selected = operation.getValue();
        String text = comment.getValue().isBlank() ? null : comment.getValue();
        Actions.run(ProductionUi.operationLabel(selected, model.type()) + ": збережено.", () -> {
            ProductionOperationResult result = service.record(model.id(), selected, new ProductionOperationRequest(rows, text),
                    user.userId(), user.username());
            lastWarnings = result.warnings();
            onDone.run();
        });
    }

    /** What went short in the last operation of the previous panel, shown after the card reloads. */
    List<String> lastWarnings() {
        return lastWarnings;
    }

    void showWarnings(List<String> list) {
        warnings.removeAll();
        if (list.isEmpty()) {
            return;
        }
        var items = new UnorderedList();
        list.forEach(w -> items.add(new ListItem(w)));
        var note = new Div(new Paragraph("Зроблено, але дечого бракувало (списано те, що було):"), items);
        note.addClassNames("note", "tone-2");
        warnings.add(note);
    }

    private String hint(ProductionOperation value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case CUT -> "Розкроєні комплекти: залишок крою зростає, тканина списується за специфікацією крою, деталі йдуть у запас.";
            case SEND_TO_SEWING -> "Розкроєне передано швачкам: залишок крою зменшується.";
            case SEWN -> ProductionModel.READY_MADE.equals(model.type())
                    ? "Товар надійшов від постачальника: залишок зростає."
                    : "Пошиті вироби: залишок зростає, деталі крою й фурнітура списуються за специфікацією пошиву.";
            case SHIP -> "Вироби пішли зі складу не через замовлення (замовлення списують самі).";
        };
    }
}
