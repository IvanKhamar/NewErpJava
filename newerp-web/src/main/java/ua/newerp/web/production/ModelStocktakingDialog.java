package ua.newerp.web.production;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.production.ComponentCountRequest;
import ua.newerp.application.production.ComponentDto;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.ProductionSizeDto;
import ua.newerp.application.production.ProductionStocktakingRequest;
import ua.newerp.application.production.ProductionStocktakingResult;
import ua.newerp.application.production.SizeCountRequest;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/**
 * Stocktaking of one model: the fields start with the stock in the books; what is changed to the counted quantity is sent.
 * Size differences go into the production log, the component stock is just set.
 */
public class ModelStocktakingDialog extends Dialog {

    final Map<Integer, BigDecimalField> cut = new LinkedHashMap<>();
    final Map<Integer, BigDecimalField> sewn = new LinkedHashMap<>();
    /** Key: component id and size id. */
    final Map<List<Integer>, BigDecimalField> components = new LinkedHashMap<>();
    final TextField comment = new TextField("Коментар");
    final Button save = new Button("Провести");

    private final ProductionService service;
    private final ProductionModelDto model;
    private final CurrentUser user;
    private final Consumer<ProductionStocktakingResult> onDone;

    public ModelStocktakingDialog(ProductionService service, ProductionModelDto model, CurrentUser user,
            Consumer<ProductionStocktakingResult> onDone) {
        this.service = service;
        this.model = model;
        this.user = user;
        this.onDone = onDone;
        setHeaderTitle("Інвентаризація: " + model.name());
        setWidth("min(48rem, 100%)");
        var hint = new Paragraph("Змініть кількості, які порахували, на фактичні. Різниця розмірів потрапить у журнал, пошите — на вітрину.");
        hint.addClassName("secondary");
        add(hint);

        boolean clothing = ProductionModel.CLOTHING.equals(model.type());
        var sizes = new Grid<ProductionSizeDto>();
        sizes.addColumn(ProductionSizeDto::name).setHeader("Розмір").setAutoWidth(true);
        if (clothing) {
            sizes.addComponentColumn(s -> cut.computeIfAbsent(s.id(), k -> field(s.cutStock()))).setHeader("Розкроєно");
        }
        sizes.addComponentColumn(s -> sewn.computeIfAbsent(s.id(), k -> field(s.sewnStock()))).setHeader("Пошито");
        sizes.addThemeVariants(GridVariant.LUMO_COMPACT);
        sizes.setAllRowsVisible(true);
        sizes.setItems(model.sizes());
        add(sizes);

        if (clothing && !model.components().isEmpty()) {
            var parts = new Grid<ComponentDto>();
            parts.addColumn(ComponentDto::name).setHeader("Деталь").setAutoWidth(true);
            for (ProductionSizeDto size : model.sizes()) {
                parts.addComponentColumn(c -> components.computeIfAbsent(List.of(c.id(), size.id()),
                        k -> field(c.stock().getOrDefault(size.id(), BigDecimal.ZERO)))).setHeader(size.name());
            }
            parts.addThemeVariants(GridVariant.LUMO_COMPACT);
            parts.setAllRowsVisible(true);
            parts.setItems(model.components());
            add(new H4("Розкроєні деталі"), parts);
        }

        comment.setWidthFull();
        comment.setMaxLength(200);
        add(comment);
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        var sizeCounts = new ArrayList<SizeCountRequest>();
        for (ProductionSizeDto size : model.sizes()) {
            BigDecimal cutCount = changed(cut.get(size.id()), size.cutStock());
            BigDecimal sewnCount = changed(sewn.get(size.id()), size.sewnStock());
            if (cutCount != null || sewnCount != null) {
                sizeCounts.add(new SizeCountRequest(size.id(), cutCount, sewnCount));
            }
        }
        var componentCounts = new ArrayList<ComponentCountRequest>();
        for (ComponentDto component : model.components()) {
            for (ProductionSizeDto size : model.sizes()) {
                BigDecimal count = changed(components.get(List.of(component.id(), size.id())),
                        component.stock().getOrDefault(size.id(), BigDecimal.ZERO));
                if (count != null) {
                    componentCounts.add(new ComponentCountRequest(component.id(), size.id(), count));
                }
            }
        }
        if (sizeCounts.isEmpty() && componentCounts.isEmpty()) {
            Messages.warning("Усе збігається з обліком: змінювати нічого.");
            return;
        }
        Actions.run(null, () -> {
            ProductionStocktakingResult result = service.recount(model.id(),
                    new ProductionStocktakingRequest(sizeCounts, componentCounts, comment.getValue()), user.userId(), user.username());
            Messages.success("Проведено: розмірів змінено " + (result.cut().size() + result.sewn().size()) + ", деталей — "
                    + result.components() + ".");
            close();
            onDone.accept(result);
        });
    }

    /** The counted value when it differs from the books; null when it matches or the field was cleared. */
    private static BigDecimal changed(BigDecimalField field, BigDecimal booked) {
        if (field == null || field.getValue() == null) {
            return null;
        }
        return field.getValue().compareTo(booked) == 0 ? null : field.getValue();
    }

    private static BigDecimalField field(BigDecimal value) {
        var field = new BigDecimalField();
        field.setValue(Fmt.plain(value));
        field.setLocale(Fmt.UK);
        field.setWidth("7rem");
        return field;
    }
}
