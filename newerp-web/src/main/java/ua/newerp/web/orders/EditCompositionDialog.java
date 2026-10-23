package ua.newerp.web.orders;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.data.value.ValueChangeMode;

import ua.newerp.application.orders.NewOrderService;
import ua.newerp.application.orders.OrderCompositionDto;
import ua.newerp.application.orders.OrderCompositionLine;
import ua.newerp.application.orders.UpdateOrderCompositionRequest;
import ua.newerp.application.orders.UpdateOrderCompositionResult;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/**
 * A new composition of an order: products, sizes, quantities, prices and sums (EditCompositionDialog.razor).
 * The new composition fully replaces the old one; the card gets the result through onSaved.
 */
public class EditCompositionDialog extends Dialog {

    final OrderLinesEditor lines;
    final BigDecimalField shippingCost = money("Доставка, грн");
    final BigDecimalField discount = money("Знижка, грн");
    final BigDecimalField prepayment = money("Завдаток, грн");
    final H4 total = new H4();
    final Button save = new Button("Зберегти");

    private final NewOrderService service;
    private final int orderId;
    private final int userId;
    private final Consumer<UpdateOrderCompositionResult> onSaved;

    public EditCompositionDialog(NewOrderService service, int orderId, int userId, Consumer<UpdateOrderCompositionResult> onSaved) {
        this.service = service;
        this.orderId = orderId;
        this.userId = userId;
        this.onSaved = onSaved;
        OrderCompositionDto composition = service.getComposition(orderId);
        setHeaderTitle("Склад замовлення");
        setWidth("min(64rem, 100%)");

        lines = new OrderLinesEditor(service::searchProducts, this::updateTotal);
        for (OrderCompositionLine line : composition.lines()) {
            lines.addLine(line.productId(), line.name(), line.model(), line.price(), line.options(), line.productOptionValueId(),
                    line.quantity(), line.price());
        }
        shippingCost.setValue(Fmt.plain(composition.shippingCost()));
        discount.setValue(Fmt.plain(composition.discount()));
        prepayment.setValue(Fmt.plain(composition.prepayment()));
        for (BigDecimalField field : List.of(shippingCost, discount, prepayment)) {
            field.addValueChangeListener(e -> updateTotal());
        }
        var sums = new HorizontalLayout(shippingCost, discount, prepayment);
        var hint = new Paragraph("Пошиті вироби перерахуються за новим складом, якщо статус замовлення їх списує.");
        hint.addClassName("secondary");
        add(lines, sums, total, hint);
        updateTotal();

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);
    }

    void save() {
        var request = new UpdateOrderCompositionRequest(lines.requests(), OrderLinesEditor.orZero(shippingCost.getValue()),
                OrderLinesEditor.orZero(discount.getValue()), OrderLinesEditor.orZero(prepayment.getValue()));
        Actions.run(null, () -> {
            UpdateOrderCompositionResult result = service.updateComposition(orderId, request, userId);
            close();
            onSaved.accept(result);
        });
    }

    BigDecimal total() {
        return lines.subTotal().add(OrderLinesEditor.orZero(shippingCost.getValue()))
                .subtract(OrderLinesEditor.orZero(discount.getValue()));
    }

    private void updateTotal() {
        total.setText("Разом: " + Fmt.money(total()));
    }

    static BigDecimalField money(String label) {
        var field = new BigDecimalField(label);
        field.setValueChangeMode(ValueChangeMode.EAGER);
        return field;
    }
}
