package ua.newerp.web.stock;

import java.math.BigDecimal;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.stock.AdjustStockRequest;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockMovementDto;
import ua.newerp.application.stock.StockService;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/** A receipt or a use of a material (AdjustStockDialog.razor); the new movement goes to onSaved. */
public class AdjustStockDialog extends Dialog {

    static final String INCOME = "Прихід";
    static final String USE = "Витрата";

    final RadioButtonGroup<String> kind = new RadioButtonGroup<>();
    final BigDecimalField quantity = new BigDecimalField();
    final TextField reason = new TextField("Причина");
    final Button save = new Button("Зберегти");

    private final StockService service;
    private final StockItemDto item;
    private final CurrentUser user;
    private final Consumer<StockMovementDto> onSaved;

    public AdjustStockDialog(StockService service, StockItemDto item, CurrentUser user, Consumer<StockMovementDto> onSaved) {
        this.service = service;
        this.item = item;
        this.user = user;
        this.onSaved = onSaved;
        setHeaderTitle("Прихід або витрата: " + item.name());
        setWidth("min(28rem, 100%)");

        kind.setItems(INCOME, USE);
        kind.setValue(INCOME);
        kind.addValueChangeListener(e -> reason.setPlaceholder(INCOME.equals(e.getValue()) ? "Прихід від постачальника"
                : "Брак, інвентаризація..."));
        quantity.setLabel("Кількість, " + item.unit());
        quantity.setWidthFull();
        reason.setPlaceholder("Прихід від постачальника");
        reason.setMaxLength(255);
        reason.setWidthFull();
        add(new Paragraph("Зараз на складі: " + Fmt.qty(item.quantity()) + " " + item.unit()), kind, quantity, reason);

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
        boolean income = INCOME.equals(kind.getValue());
        String text = reason.getValue().isBlank() ? (income ? INCOME : USE) : reason.getValue();
        Actions.run(null, () -> {
            StockMovementDto movement = service.adjust(item.id(), new AdjustStockRequest(income ? amount : amount.negate(), text),
                    user.userId(), user.username());
            Messages.success(item.name() + ": тепер " + Fmt.qty(movement.quantityAfter()) + " " + item.unit() + ".");
            close();
            onSaved.accept(movement);
        });
    }
}
