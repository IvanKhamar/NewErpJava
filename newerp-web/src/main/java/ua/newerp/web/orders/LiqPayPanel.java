package ua.newerp.web.orders;

import java.math.BigDecimal;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;

import ua.newerp.application.payments.CreatePaymentLinkRequest;
import ua.newerp.application.payments.LiqPayService;
import ua.newerp.application.payments.LiqPayStatusDto;
import ua.newerp.application.payments.PaymentLinkDto;
import ua.newerp.domain.payments.LiqPayState;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Clipboard;
import ua.newerp.web.ui.Panel;

/** Card payment (LiqPayPanel.razor): the manager creates a LiqPay link, sends it to the customer and later checks the payment. */
public class LiqPayPanel extends Panel {

    final BigDecimalField amount = new BigDecimalField("Сума, грн");
    final Button createLink = new Button("Створити посилання", VaadinIcon.LINK.create());
    final Button check = new Button("Перевірити оплату", VaadinIcon.REFRESH.create());
    final Div link = new Div();
    final Div status = new Div();

    private final LiqPayService service;
    private final int orderId;

    public LiqPayPanel(LiqPayService service, int orderId, BigDecimal total) {
        super("Оплата карткою (LiqPay)");
        this.service = service;
        this.orderId = orderId;
        amount.setValue(Fmt.plain(total));
        amount.setWidth("10rem");
        createLink.addClickListener(e -> createLink());
        check.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        check.addClickListener(e -> check());
        var row = new HorizontalLayout(amount, createLink, check);
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        row.getStyle().set("flex-wrap", "wrap");
        add(row, link, status);
    }

    void createLink() {
        BigDecimal value = amount.getValue();
        Actions.run(null, () -> show(service.createLink(orderId, new CreatePaymentLinkRequest(value))));
    }

    void check() {
        Actions.run(null, () -> show(service.status(orderId)));
    }

    private void show(PaymentLinkDto dto) {
        link.removeAll();
        if (dto.isTest()) {
            var test = new Paragraph("Ключів LiqPay не налаштовано: посилання тестове, оплатити за ним не можна.");
            test.addClassName("note");
            link.add(test);
        }
        var copy = new Button(VaadinIcon.COPY.create(), e -> Clipboard.copy(dto.url(), "Посилання скопійовано."));
        copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        copy.setAriaLabel("Скопіювати посилання");
        var row = new HorizontalLayout(new Span(Fmt.money(dto.amount()) + " ·"),
                new Anchor(dto.url(), "відкрити сторінку оплати", AnchorTarget.BLANK), copy);
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        link.add(row);
    }

    private void show(LiqPayStatusDto dto) {
        status.removeAll();
        String text = dto.label() + (dto.amount() == null ? "" : " · " + Fmt.money(dto.amount()))
                + (dto.paidAt() == null ? "" : " · " + Fmt.date(dto.paidAt()));
        var badge = new Span(text);
        badge.addClassNames("status-badge", dto.state() == LiqPayState.PAID.ordinal() ? "tone-1" : "tone-0");
        status.add(badge);
    }
}
