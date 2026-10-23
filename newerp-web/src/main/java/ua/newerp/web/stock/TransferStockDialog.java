package ua.newerp.web.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.stock.PlaceDto;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockLocationService;
import ua.newerp.application.stock.TransferStockRequest;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/** Moves a material from one place to another; the total stays the same. */
public class TransferStockDialog extends Dialog {

    final Select<PlaceDto> from = new Select<>();
    final Select<PlaceDto> to = new Select<>();
    final BigDecimalField quantity = new BigDecimalField();
    final TextField comment = new TextField("Коментар");
    final Button save = new Button("Перемістити");

    private final StockLocationService service;
    private final StockItemDto item;
    private final CurrentUser user;
    private final Runnable onSaved;

    /** places: the main stock first, then every warehouse with its quantity of this item. */
    public TransferStockDialog(StockLocationService service, List<PlaceDto> places, StockItemDto item, CurrentUser user,
            Runnable onSaved) {
        this.service = service;
        this.item = item;
        this.user = user;
        this.onSaved = onSaved;
        setHeaderTitle("Перемістити: " + item.name());
        setWidth("min(28rem, 100%)");

        from.setLabel("Звідки");
        to.setLabel("Куди");
        for (Select<PlaceDto> select : List.of(from, to)) {
            select.setItems(places);
            select.setItemLabelGenerator(p -> p.name() + " (" + Fmt.qty(p.quantity()) + " " + item.unit() + ")");
            select.setWidthFull();
        }
        from.setValue(places.getFirst());
        to.setValue(places.get(1));
        quantity.setLabel("Кількість, " + item.unit());
        quantity.setWidthFull();
        comment.setMaxLength(255);
        comment.setWidthFull();
        add(from, to, quantity, comment);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        BigDecimal amount = quantity.getValue();
        if (amount == null || amount.signum() <= 0) {
            Messages.error("Вкажіть кількість, більшу за нуль.");
            return;
        }
        if (Objects.equals(from.getValue(), to.getValue())) {
            Messages.error("Виберіть різні склади.");
            return;
        }
        Actions.run(null, () -> {
            service.transfer(item.id(), new TransferStockRequest(from.getValue().warehouseId(), to.getValue().warehouseId(), amount,
                    comment.getValue()), user.userId(), user.username());
            Messages.success("Переміщено " + Fmt.qty(amount) + " " + item.unit() + ": " + from.getValue().name() + " → "
                    + to.getValue().name() + ".");
            close();
            onSaved.run();
        });
    }
}
