package ua.newerp.web.finance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.payments.BankPaymentDto;
import ua.newerp.application.payments.BankPaymentService;
import ua.newerp.application.payments.LinkPaymentRequest;
import ua.newerp.application.payments.PaymentSuggestionDto;
import ua.newerp.domain.payments.MatchReason;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/** Which order a payment belongs to (MatchPaymentDialog.razor): suggestions, most likely first, and a search by customer or number. */
public class MatchPaymentDialog extends Dialog {

    final TextField customer = new TextField("Пошук: прізвище, телефон чи № замовлення");
    final Grid<PaymentSuggestionDto> suggestions = new Grid<>();
    private final BankPaymentService service;
    private final BankPaymentDto payment;
    private final Consumer<BankPaymentDto> onLinked;

    public MatchPaymentDialog(BankPaymentService service, BankPaymentDto payment, Consumer<BankPaymentDto> onLinked) {
        this.service = service;
        this.payment = payment;
        this.onLinked = onLinked;
        setHeaderTitle("Якому замовленню належить платіж?");
        setWidth("min(64rem, 100%)");

        var summary = new Paragraph(Fmt.money(payment.amount()) + " · " + Fmt.date(payment.timestamp()) + " · " + payment.payer());
        summary.addClassName(LumoUtility.FontWeight.BOLD);
        var description = new Paragraph(payment.description());
        description.addClassNames("secondary", "pre-line");
        customer.setMinWidth("22rem");
        customer.addKeyPressListener(Key.ENTER, e -> load());
        var search = new HorizontalLayout(customer, new Button("Шукати", e -> load()));
        search.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        suggestions.addComponentColumn(s -> lines("№" + s.orderId(), Fmt.date(s.dateAdded()) + " · " + s.status())).setHeader("Замовлення")
                .setAutoWidth(true);
        suggestions.addComponentColumn(s -> lines(s.customer(), s.telephone())).setHeader("Клієнт").setAutoWidth(true);
        suggestions.addComponentColumn(s -> lines(Fmt.money(s.total()),
                s.prepayment().signum() > 0 ? "завдаток " + Fmt.money(s.prepayment()) : "")).setHeader("Сума").setAutoWidth(true);
        suggestions.addComponentColumn(MatchPaymentDialog::reasonChips).setHeader("Що збіглося").setFlexGrow(1);
        suggestions.addComponentColumn(s -> {
            var link = new Button("Прив'язати", e -> link(s));
            link.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            return link;
        }).setAutoWidth(true);
        suggestions.addThemeVariants(GridVariant.LUMO_COMPACT);
        suggestions.setEmptyStateText("Схожих замовлень не знайшлося. Спробуйте пошук.");
        suggestions.setAllRowsVisible(true);

        add(summary, description, search, suggestions);
        getFooter().add(new Button("Закрити", e -> close()));
        load();
    }

    void load() {
        Actions.run(null, () -> suggestions.setItems(service.suggest(payment.id(), customer.getValue())));
    }

    void link(PaymentSuggestionDto order) {
        Actions.run(null, () -> {
            BankPaymentDto linked = service.link(payment.id(), new LinkPaymentRequest(order.orderId()));
            close();
            onLinked.accept(linked);
        });
    }

    static List<String> reasons(int flags) {
        var list = new ArrayList<String>();
        if ((flags & MatchReason.ORDER_NUMBER) != 0) {
            list.add("номер у призначенні");
        }
        if ((flags & MatchReason.TOTAL) != 0) {
            list.add("сума");
        }
        if ((flags & MatchReason.PREPAYMENT) != 0) {
            list.add("завдаток");
        }
        if ((flags & MatchReason.PHONE) != 0) {
            list.add("телефон");
        }
        if ((flags & MatchReason.NAME) != 0) {
            list.add("прізвище");
        }
        return list;
    }

    private static FlexLayout reasonChips(PaymentSuggestionDto suggestion) {
        var chips = new FlexLayout();
        chips.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        chips.addClassName(LumoUtility.Gap.XSMALL);
        reasons(suggestion.reasons()).forEach(r -> chips.add(chip(r, "tone-0")));
        if (suggestion.linkedPaymentId() != null) {
            chips.add(chip("уже має платіж", "tone-2"));
        }
        return chips;
    }

    private static Span chip(String text, String tone) {
        var chip = new Span(text);
        chip.addClassNames("status-badge", tone);
        return chip;
    }

    private static Div lines(String first, String second) {
        var caption = new Span(second == null ? "" : second);
        caption.addClassNames("secondary", LumoUtility.FontSize.SMALL);
        return new Div(new Div(first), caption);
    }
}
