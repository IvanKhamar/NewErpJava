package ua.newerp.web.marketing;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.marketing.SaveVoucherRequest;
import ua.newerp.application.marketing.VoucherCardDto;
import ua.newerp.application.marketing.VoucherService;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;

/** A new voucher, or an existing one with its spending history, the SMS to the recipient and deleting. */
public class VoucherDialog extends Dialog {

    final TextField code = new TextField("Код");
    final BigDecimalField amount = new BigDecimalField("Сума, грн");
    final TextField toName = new TextField("Кому");
    final TextField toEmail = new TextField("Email отримувача");
    final TextField fromName = new TextField("Від кого");
    final TextField fromEmail = new TextField("Email відправника");
    final Select<LookupItem> theme = new Select<>();
    final TextArea message = new TextArea("Побажання");
    final Checkbox active = new Checkbox("Діє", true);
    final TextField phone = new TextField("Телефон отримувача");
    final Button save = new Button("Зберегти");
    final Button sendSms = new Button("Надіслати SMS", VaadinIcon.ENVELOPE.create());
    final Button delete = new Button("Видалити", VaadinIcon.TRASH.create());
    private final VoucherService service;
    private final Integer id;
    private final Runnable onChange;

    /** @param id null for a new voucher */
    public VoucherDialog(VoucherService service, Integer id, Runnable onChange) {
        this.service = service;
        this.id = id;
        this.onChange = onChange;
        setWidth("min(48rem, 100%)");
        code.setMaxLength(10);
        code.setHelperText(id == null ? "Порожній — згенерується" : "");
        theme.setLabel("Тема");
        theme.setItems(service.themes());
        theme.setItemLabelGenerator(t -> t == null ? "Без теми" : t.name());
        theme.setEmptySelectionAllowed(true);
        message.setMaxHeight("8rem");
        var form = new FormLayout(code, amount, toName, toEmail, fromName, fromEmail, theme, active, message);
        form.setColspan(message, 2);
        add(form);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        getFooter().add(new Button("Скасувати", e -> close()), save);

        if (id == null) {
            setHeaderTitle("Новий подарунковий сертифікат");
            return;
        }
        VoucherCardDto voucher = service.get(id);
        setHeaderTitle("Сертифікат " + voucher.code());
        code.setValue(voucher.code());
        amount.setValue(voucher.amount());
        toName.setValue(voucher.toName());
        toEmail.setValue(voucher.toEmail());
        fromName.setValue(voucher.fromName());
        fromEmail.setValue(voucher.fromEmail());
        theme.getListDataView().getItems().filter(t -> t.id() == voucher.themeId()).findFirst().ifPresent(theme::setValue);
        message.setValue(voucher.message());
        active.setValue(voucher.active());

        var balance = new Paragraph("Залишок: " + Fmt.money(voucher.balance()) + (voucher.usable() ? ""
                : voucher.active() ? " (замовлення, в якому його купили, ще не оплачено)" : " (вимкнено)"));
        var history = new Grid<VoucherCardDto.HistoryRow>();
        history.addColumn(h -> h.orderId() == 0 ? "" : "№" + h.orderId()).setHeader("Замовлення").setAutoWidth(true);
        history.addColumn(VoucherCardDto.HistoryRow::customer).setHeader("Покупець").setFlexGrow(1);
        history.addColumn(h -> Fmt.money(h.amount())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        history.addColumn(h -> Fmt.date(h.date())).setHeader("Коли").setAutoWidth(true);
        history.addThemeVariants(GridVariant.LUMO_COMPACT);
        history.setAllRowsVisible(true);
        history.setEmptyStateText("Ще не використовували.");
        history.setItems(voucher.history());
        add(new H4("Залишок і списання"), balance, history);

        sendSms.addClickListener(e -> sms());
        var smsRow = new HorizontalLayout(phone, sendSms);
        smsRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        add(new H4("SMS з кодом"), smsRow);

        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.setEnabled(voucher.history().isEmpty());
        delete.addClickListener(e -> Confirm.ask("Видалити сертифікат?", "Сертифікат " + voucher.code() + " буде видалено, сайт його більше не прийме.",
                "Видалити", "Залишити", () -> Actions.run("Сертифікат видалено.", () -> {
                    service.delete(id);
                    close();
                    onChange.run();
                })));
        getFooter().addComponentAsFirst(delete);
    }

    void save() {
        var request = new SaveVoucherRequest(code.getValue(), fromName.getValue(), fromEmail.getValue(), toName.getValue(), toEmail.getValue(),
                theme.getValue() == null ? 0 : theme.getValue().id(), message.getValue(), amount.getValue(), active.getValue());
        Actions.run(null, () -> {
            VoucherCardDto saved = id == null ? service.create(request) : service.update(id, request);
            Messages.success("Сертифікат " + saved.code() + " збережено.");
            close();
            onChange.run();
        });
    }

    void sms() {
        Actions.run(null, () -> {
            var result = service.sendSms(id, phone.getValue());
            if (result.sent()) {
                Messages.success("SMS надіслано.");
            } else {
                Messages.error("SMS не надіслано: " + result.response());
            }
        });
    }
}
