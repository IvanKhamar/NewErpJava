package ua.newerp.web.marketing;

import java.util.Set;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.marketing.BirthdayContactDto;
import ua.newerp.application.marketing.BirthdaySmsRequest;
import ua.newerp.application.marketing.BulkSmsRequest;
import ua.newerp.application.marketing.CustomerOrderDto;
import ua.newerp.application.marketing.MarketingService;
import ua.newerp.domain.marketing.BirthdayPeriod;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/** Birthday greetings (Birthdays.razor): one person with an optional personal coupon, or one text to several people. */
@Route("marketing/birthdays")
@PageTitle("Дні народження — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class BirthdaysView extends VerticalLayout {

    static final int SMS_MAX_LENGTH = 670;

    final RadioButtonGroup<BirthdayPeriod> period = new RadioButtonGroup<>();
    final Grid<BirthdayContactDto> grid = new Grid<>();
    final Div bulk = new Div();
    final TextArea bulkText = sms();
    final Button sendBulk = new Button("Надіслати вибраним");
    final Div greeting = new Div();
    final TextField coupon = new TextField("Купон");
    final Button newCoupon = new Button("Згенерувати купон");
    final Button noCoupon = new Button("Без купона");
    final TextArea text = sms();
    final Button send = new Button("Надіслати");
    final Grid<CustomerOrderDto> orders = new Grid<>();
    BirthdayContactDto current;

    private final MarketingService service;
    private final H4 bulkTitle = new H4();
    private final H4 greetingTitle = new H4();

    public BirthdaysView(MarketingService service) {
        this.service = service;
        setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        var hint = new Paragraph("Клієнти, які вказали день народження в замовленні. Привітання можна надіслати з персональним купоном на "
                + "знижку (одне використання, діє обмежений час) або одним текстом кільком клієнтам одразу.");
        hint.addClassName("secondary");

        period.setItems(BirthdayPeriod.values());
        period.setItemLabelGenerator(p -> switch (p) {
            case TODAY -> "Сьогодні";
            case TOMORROW -> "Завтра";
            case WEEK -> "Тиждень";
            case MONTH -> "Цей місяць";
            case ALL -> "Усі";
        });
        period.setValue(BirthdayPeriod.TODAY);
        period.addValueChangeListener(e -> load());

        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addComponentColumn(c -> {
            var name = new Div(c.firstName() + " " + c.lastName());
            if (c.instagram() != null && !c.instagram().isEmpty()) {
                var instagram = new Span(c.instagram());
                instagram.addClassNames("secondary", LumoUtility.FontSize.SMALL);
                name.add(new Div(instagram));
            }
            return name;
        }).setHeader("Клієнт").setFlexGrow(1);
        grid.addColumn(BirthdayContactDto::phone).setHeader("Телефон").setAutoWidth(true);
        grid.addColumn(BirthdayContactDto::city).setHeader("Місто").setAutoWidth(true);
        grid.addColumn(c -> "%02d.%02d".formatted(c.day(), c.month())).setHeader("День народження").setAutoWidth(true);
        grid.addComponentColumn(c -> {
            var sent = new Span(c.sentThisYear() > 0 ? String.valueOf(c.sentThisYear()) : "");
            if (c.sentThisYear() > 0) {
                sent.addClassNames("status-badge", "tone-1");
            }
            return sent;
        }).setHeader("Привітань цього року").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Іменинників за цей період немає.");
        grid.setAllRowsVisible(true);
        grid.addItemClickListener(e -> select(e.getItem()));
        grid.addSelectionListener(e -> showBulk(e.getAllSelectedItems()));

        sendBulk.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendBulk.setEnabled(false);
        bulkText.addValueChangeListener(e -> sendBulk.setEnabled(!e.getValue().isBlank()));
        sendBulk.addClickListener(e -> sendBulk());
        bulk.add(bulkTitle, bulkText, sendBulk);
        bulk.addClassName("panel");
        bulk.setVisible(false);

        coupon.setReadOnly(true);
        coupon.setMaxWidth("12rem");
        newCoupon.addClickListener(e -> newCoupon());
        noCoupon.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        noCoupon.setVisible(false);
        noCoupon.addClickListener(e -> {
            coupon.clear();
            noCoupon.setVisible(false);
            text.setValue(greetingText(current.firstName(), null));
        });
        var couponRow = new HorizontalLayout(coupon, newCoupon, noCoupon);
        couponRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        text.addValueChangeListener(e -> send.setEnabled(!e.getValue().isBlank()));
        send.addClickListener(e -> send());
        orders.addComponentColumn(o -> new Anchor("orders/" + o.orderId(), "№" + o.orderId())).setAutoWidth(true);
        orders.addColumn(o -> Fmt.date(o.dateAdded())).setAutoWidth(true);
        orders.addColumn(CustomerOrderDto::status).setAutoWidth(true);
        orders.addColumn(CustomerOrderDto::products).setFlexGrow(1);
        orders.addColumn(o -> Fmt.money(o.total())).setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        orders.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_WRAP_CELL_CONTENT);
        orders.setAllRowsVisible(true);
        greeting.add(greetingTitle, couponRow, text, send, new H4("Замовлення клієнта"), orders);
        greeting.addClassName("panel");
        greeting.setVisible(false);

        add(new H2("Дні народження"), hint, period, grid, bulk, greeting);
        load();
    }

    void select(BirthdayContactDto contact) {
        current = contact;
        greetingTitle.setText("Привітання: " + contact.firstName() + " " + contact.lastName() + ", " + contact.phone());
        coupon.clear();
        noCoupon.setVisible(false);
        text.setValue(greetingText(contact.firstName(), null));
        orders.setItems(service.customerOrders(contact.phone()));
        greeting.setVisible(true);
    }

    void newCoupon() {
        Actions.run(null, () -> {
            String code = service.newCouponCode().code();
            coupon.setValue(code);
            noCoupon.setVisible(true);
            text.setValue(greetingText(current.firstName(), code));
        });
    }

    void send() {
        String code = coupon.isEmpty() ? null : coupon.getValue();
        Actions.run(null, () -> {
            var result = service.sendBirthdaySms(new BirthdaySmsRequest(current.phone(), text.getValue(), code));
            if (result.sent()) {
                Messages.success("Привітання надіслано." + (result.couponId() == null ? "" : " Купон " + code + " створено."));
            } else {
                Messages.warning("SMS не пішло: " + result.response());
            }
            load();
        });
    }

    void sendBulk() {
        var phones = grid.getSelectedItems().stream().map(BirthdayContactDto::phone).toList();
        Actions.run(null, () -> {
            var result = service.sendBulk(new BulkSmsRequest(phones, bulkText.getValue()));
            String message = "Надіслано: " + result.sent() + ", не пішло: " + result.notSent() + ".";
            if (result.notSent() == 0) {
                Messages.success(message);
            } else {
                Messages.warning(message);
            }
            bulkText.clear();
            load();
        });
    }

    static String greetingText(String name, String code) {
        return name + ", вітаємо з днем народження!" + (code == null ? "" : " Ваш подарунок — знижка за промокодом " + code + ".") + " Vidro";
    }

    private void showBulk(Set<BirthdayContactDto> selected) {
        bulkTitle.setText("Однаковий текст вибраним: " + selected.size());
        bulk.setVisible(selected.size() > 1);
    }

    private void load() {
        current = null;
        greeting.setVisible(false);
        grid.deselectAll();
        grid.setItems(service.birthdays(period.getValue()));
    }

    private static TextArea sms() {
        var area = new TextArea("Текст SMS");
        area.setMaxLength(SMS_MAX_LENGTH);
        area.setValueChangeMode(ValueChangeMode.EAGER);
        area.setWidthFull();
        area.addValueChangeListener(e -> area.setHelperText(e.getValue().length() + " / " + SMS_MAX_LENGTH));
        return area;
    }
}
