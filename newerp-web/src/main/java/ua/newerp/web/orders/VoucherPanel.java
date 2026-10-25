package ua.newerp.web.orders;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.marketing.OrderVoucherDto;
import ua.newerp.application.marketing.VoucherService;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Panel;

/** A gift voucher paying part of the order: the one spent, or a code to spend (the order's total becomes smaller by it). */
public class VoucherPanel extends Panel {

    final TextField code = new TextField();
    final BigDecimalField amount = new BigDecimalField();
    final Button redeem = new Button("Списати");
    final Button release = new Button("Зняти", VaadinIcon.CLOSE.create());

    public VoucherPanel(VoucherService service, int orderId, Runnable onChange) {
        super("Подарунковий сертифікат");
        OrderVoucherDto spent = service.forOrder(orderId).orElse(null);
        if (spent != null) {
            var info = new Span(spent.code() + ": −" + Fmt.money(spent.amount()) + " (на сертифікаті лишилось " + Fmt.money(spent.voucherBalance()) + ")");
            release.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            release.addClickListener(e -> Confirm.ask("Зняти сертифікат?", Fmt.money(spent.amount()) + " повернеться на сертифікат "
                    + spent.code() + ", а сума замовлення збільшиться.", "Зняти", "Залишити",
                    () -> Actions.run("Сертифікат знято.", () -> {
                        service.release(orderId);
                        onChange.run();
                    })));
            var row = new HorizontalLayout(info, release);
            row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            add(row);
            return;
        }
        code.setPlaceholder("Код");
        code.setWidth("9rem");
        amount.setPlaceholder("Сума (усе)");
        amount.setWidth("9rem");
        redeem.addClickListener(e -> Actions.run(null, () -> {
            OrderVoucherDto result = service.redeem(orderId, code.getValue(), amount.getValue());
            Messages.success("Списано " + Fmt.money(result.amount()) + " із сертифіката " + result.code() + ".");
            onChange.run();
        }));
        var row = new HorizontalLayout(code, amount, redeem);
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        add(row);
    }
}
