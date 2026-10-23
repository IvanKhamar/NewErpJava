package ua.newerp.web.production;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.production.ProductionLocationService;
import ua.newerp.application.production.SewnTransferDto;
import ua.newerp.application.production.SewnTransferRequest;
import ua.newerp.application.production.SizePlacesDto;
import ua.newerp.application.stock.PlaceDto;
import ua.newerp.web.Fmt;
import ua.newerp.web.stock.Places;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Panel;

/** Where the sewn pieces of a model lie (main stock and each warehouse), moving them between places, and the moves made. */
class SewnPlacesPanel extends Div {

    final Grid<SizePlacesDto> grid = new Grid<>();
    final Select<SizePlacesDto> size = new Select<>();
    final Select<LookupItem> from;
    final Select<LookupItem> to;
    final BigDecimalField quantity = new BigDecimalField("Кількість");
    final TextField comment = new TextField("Коментар");
    final Button move = new Button("Перемістити");

    private final ProductionLocationService service;
    private final int modelId;
    private final CurrentUser user;
    private final Runnable onDone;

    SewnPlacesPanel(ProductionLocationService service, int modelId, List<SizePlacesDto> sizes, List<LocationDto> warehouses,
            CurrentUser user, Runnable onDone) {
        this.service = service;
        this.modelId = modelId;
        this.user = user;
        this.onDone = onDone;
        var hint = new Paragraph("Пошите разом — це залишок на вітрині; основний склад — усе, що не лежить на інших складах. "
                + "Прихід на склад і відвантаження зі складу — в операціях, переміщення — тут.");
        hint.addClassName("secondary");

        List<LocationDto> sorted = warehouses.stream()
                .sorted(Comparator.comparing(LocationDto::name, Collator.getInstance(Fmt.UK))).toList();
        grid.addColumn(SizePlacesDto::name).setHeader("Розмір").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(s -> Fmt.qty(s.total())).setHeader("Разом").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(s -> Fmt.qty(s.places().getFirst().quantity())).setHeader(Places.MAIN.name())
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        for (LocationDto warehouse : sorted) {
            grid.addColumn(s -> qty(at(s, warehouse.id()))).setHeader(warehouse.name()).setTextAlign(ColumnTextAlign.END)
                    .setAutoWidth(true);
        }
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setItems(sizes);
        grid.setAllRowsVisible(true);

        size.setLabel("Розмір");
        size.setItems(sizes);
        size.setItemLabelGenerator(SizePlacesDto::name);
        if (!sizes.isEmpty()) {
            size.setValue(sizes.getFirst());
        }
        from = Places.select("Звідки", warehouses);
        to = Places.select("Куди", warehouses);
        if (!sorted.isEmpty()) {
            to.setValue(new LookupItem(sorted.getFirst().id(), sorted.getFirst().name()));
        }
        quantity.setLocale(Fmt.UK);
        quantity.setWidth("8rem");
        comment.setMaxLength(255);
        move.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        move.addClickListener(e -> move());
        var form = new HorizontalLayout(size, from, to, quantity, comment, move);
        form.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        form.setWrap(true);

        add(hint, grid, new Panel("Перемістити між складами", form));
        List<SewnTransferDto> moves = service.transfers(modelId);
        if (!moves.isEmpty()) {
            add(new Panel("Переміщення", transfers(moves)));
        }
    }

    void move() {
        BigDecimal amount = quantity.getValue();
        if (size.getValue() == null || amount == null || amount.signum() <= 0) {
            Messages.error("Виберіть розмір і вкажіть кількість, більшу за нуль.");
            return;
        }
        Integer source = Places.warehouseId(from);
        Integer target = Places.warehouseId(to);
        if (Objects.equals(source, target)) {
            Messages.error("Виберіть різні склади.");
            return;
        }
        String text = comment.getValue().isBlank() ? null : comment.getValue();
        Actions.run("Переміщено " + Fmt.qty(amount) + " шт. розміру " + size.getValue().name() + ": " + from.getValue().name() + " → "
                + to.getValue().name() + ".", () -> {
                    service.transfer(modelId, new SewnTransferRequest(size.getValue().sizeId(), source, target, amount, text),
                            user.userId(), user.username());
                    onDone.run();
                });
    }

    private static BigDecimal at(SizePlacesDto size, int warehouseId) {
        Map<Integer, BigDecimal> byId = size.places().stream().filter(p -> p.warehouseId() != null)
                .collect(Collectors.toMap(PlaceDto::warehouseId, PlaceDto::quantity));
        return byId.getOrDefault(warehouseId, BigDecimal.ZERO);
    }

    private static String qty(BigDecimal value) {
        return value.signum() == 0 ? "" : Fmt.qty(value);
    }

    private static Component transfers(List<SewnTransferDto> list) {
        var grid = new Grid<SewnTransferDto>();
        grid.addColumn(t -> Fmt.date(t.createdAt())).setHeader("Коли").setAutoWidth(true);
        grid.addColumn(SewnTransferDto::sizeName).setHeader("Розмір").setAutoWidth(true);
        grid.addColumn(t -> t.fromName() + " → " + t.toName()).setHeader("Звідки → куди").setAutoWidth(true);
        grid.addColumn(t -> Fmt.qty(t.quantity())).setHeader("Скільки").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(SewnTransferDto::comment).setHeader("Коментар").setFlexGrow(1);
        grid.addColumn(SewnTransferDto::username).setHeader("Хто").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setItems(list);
        grid.setAllRowsVisible(true);
        return grid;
    }
}
