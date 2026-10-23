package ua.newerp.web.orders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.delivery.CityDto;
import ua.newerp.application.delivery.CreateShipmentRequest;
import ua.newerp.application.delivery.DropoffPointOption;
import ua.newerp.application.delivery.SenderOption;
import ua.newerp.application.delivery.ShipmentDto;
import ua.newerp.application.delivery.ShipmentFormDto;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.delivery.WarehouseDto;
import ua.newerp.domain.delivery.SeatPreset;
import ua.newerp.domain.delivery.ShipmentSeat;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Search;

/**
 * A new Nova Poshta waybill to a branch or parcel locker (CreateShipmentDialog.razor). The fields are filled in from
 * the order; the waybill is created in Nova Poshta and the card gets it through onCreated.
 */
public class CreateShipmentDialog extends Dialog {

    final Select<SenderOption> sender = new Select<>();
    final Select<DropoffPointOption> dropoffPoint = new Select<>();
    final TextField lastName = new TextField("Прізвище");
    final TextField firstName = new TextField("Ім'я");
    final TextField middleName = new TextField("По батькові");
    final TextField phone = new TextField("Телефон");
    final ComboBox<CityDto> city = new ComboBox<>("Місто");
    final ComboBox<WarehouseDto> warehouse = new ComboBox<>("Відділення чи поштомат");
    final BigDecimalField declaredCost = new BigDecimalField("Оголошена вартість, грн");
    final BigDecimalField cashOnDelivery = new BigDecimalField("Накладений платіж, грн");
    final Checkbox senderPays = new Checkbox("Доставку оплачуємо ми");
    final List<SeatRow> seats = new ArrayList<>();
    final Button create = new Button("Створити ТТН");

    private final Div seatRows = new Div();
    private final ShipmentService service;
    private final int orderId;
    private final int userId;
    private final Consumer<ShipmentDto> onCreated;

    public CreateShipmentDialog(ShipmentService service, int orderId, int userId, Consumer<ShipmentDto> onCreated) {
        this.service = service;
        this.orderId = orderId;
        this.userId = userId;
        this.onCreated = onCreated;
        ShipmentFormDto form = service.form(orderId);
        setHeaderTitle("Нова ТТН");
        setWidth("min(60rem, 100%)");

        if (!form.canCreate()) {
            add(note("Ключ API Нової Пошти не задано, тож ТТН створити не можна.", LumoUtility.TextColor.ERROR));
        } else if (form.testMode()) {
            add(note("Ключа API немає: створиться лише тестова ТТН (номер на 00), у Новій Пошті її не буде.", null));
        }

        sender.setLabel("Відправник");
        sender.setItems(form.senders());
        sender.setItemLabelGenerator(SenderOption::name);
        sender.setValue(form.senders().stream().filter(SenderOption::isDefault).findFirst()
                .orElse(form.senders().isEmpty() ? null : form.senders().getFirst()));
        dropoffPoint.setLabel("Місце відправки");
        dropoffPoint.setItems(form.dropoffPoints());
        dropoffPoint.setItemLabelGenerator(DropoffPointOption::name);
        dropoffPoint.setValue(form.dropoffPoints().stream().filter(DropoffPointOption::isDefault).findFirst()
                .orElse(form.dropoffPoints().isEmpty() ? null : form.dropoffPoints().getFirst()));
        if (form.senders().size() > 1 || form.dropoffPoints().size() > 1) {
            add(row(sender, dropoffPoint));
        }

        lastName.setValue(form.lastName());
        firstName.setValue(form.firstName());
        phone.setValue(form.phone());
        add(heading("Отримувач"), row(lastName, firstName, middleName, phone));

        Search.bind(city, 2, service::searchCities);
        city.setItemLabelGenerator(CityDto::name);
        city.setValue(form.city());
        Search.bind(warehouse, 0, text -> city.getValue() == null ? List.of() : service.searchWarehouses(city.getValue().ref(), text));
        warehouse.setItemLabelGenerator(WarehouseDto::name);
        warehouse.setValue(form.warehouse());
        warehouse.setMinWidth("22rem");
        warehouse.setEnabled(city.getValue() != null);
        // A city or branch from the order that was not found in the directory: show what the customer wrote.
        if (form.city() == null && !form.cityName().isEmpty()) {
            city.setHelperText("У замовленні: " + form.cityName());
        }
        if (form.warehouse() == null && !form.warehouseName().isEmpty()) {
            warehouse.setHelperText("У замовленні: " + form.warehouseName());
        }
        city.addValueChangeListener(e -> {
            String oldRef = e.getOldValue() == null ? null : e.getOldValue().ref();
            String newRef = e.getValue() == null ? null : e.getValue().ref();
            if (newRef == null || !newRef.equals(oldRef)) {
                warehouse.clear(); // a branch of another city no longer fits
            }
            warehouse.setEnabled(e.getValue() != null);
        });
        add(row(city, warehouse));

        declaredCost.setValue(Fmt.plain(form.declaredCost()));
        cashOnDelivery.setValue(Fmt.plain(form.cashOnDelivery()));
        cashOnDelivery.setHelperText("0 — без накладеного платежу");
        var payment = row(declaredCost, cashOnDelivery, senderPays);
        payment.setAlignItems(FlexComponent.Alignment.CENTER);
        add(heading("Оплата"), payment);

        var seatButtons = new HorizontalLayout(new H4("Місця"));
        seatButtons.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        for (SeatPreset preset : form.seatPresets()) {
            seatButtons.add(new Button("+ " + preset.name(), e -> addSeat(preset.seat())));
        }
        seatButtons.add(new Button("+ Інша", e -> addSeat(new ShipmentSeat(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))));
        add(seatButtons, seatRows);
        if (!form.seatPresets().isEmpty()) {
            addSeat(form.seatPresets().getFirst().seat());
        }

        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setEnabled(form.canCreate());
        create.addClickListener(e -> create());
        getFooter().add(new Button("Скасувати", e -> close()), create);
    }

    void create() {
        var request = new CreateShipmentRequest(firstName.getValue(), lastName.getValue(), middleName.getValue(), phone.getValue(),
                city.getValue() == null ? "" : city.getValue().ref(), warehouse.getValue() == null ? "" : warehouse.getValue().ref(),
                declaredCost.getValue(), cashOnDelivery.getValue(), senderPays.getValue(), seats.stream().map(SeatRow::toSeat).toList(),
                sender.getValue() == null ? null : sender.getValue().id(),
                dropoffPoint.getValue() == null ? null : dropoffPoint.getValue().id());
        Actions.run(null, () -> {
            ShipmentDto shipment = service.create(orderId, request, userId);
            close();
            onCreated.accept(shipment);
        });
    }

    void addSeat(ShipmentSeat seat) {
        var row = new SeatRow(seat);
        seats.add(row);
        seatRows.add(row.layout);
    }

    private static FlexLayout row(com.vaadin.flow.component.Component... components) {
        var row = new FlexLayout(components);
        row.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        row.addClassName(LumoUtility.Gap.MEDIUM);
        return row;
    }

    private static H4 heading(String text) {
        var heading = new H4(text);
        heading.addClassName(LumoUtility.Margin.Top.MEDIUM);
        return heading;
    }

    private static Paragraph note(String text, String color) {
        var note = new Paragraph(text);
        note.addClassName("note");
        if (color != null) {
            note.addClassName(color);
        }
        return note;
    }

    /** A seat (box) that can be edited; ShipmentSeat itself is an immutable record. */
    final class SeatRow {
        final BigDecimalField weight = field("Вага, кг");
        final BigDecimalField width = field("Ширина, см");
        final BigDecimalField length = field("Довжина, см");
        final BigDecimalField height = field("Висота, см");
        final HorizontalLayout layout = new HorizontalLayout();

        SeatRow(ShipmentSeat seat) {
            weight.setValue(Fmt.plain(seat.weight()));
            width.setValue(Fmt.plain(seat.width()));
            length.setValue(Fmt.plain(seat.length()));
            height.setValue(Fmt.plain(seat.height()));
            var remove = new Button(VaadinIcon.TRASH.create(), e -> {
                seats.remove(this);
                seatRows.remove(layout);
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            remove.setAriaLabel("Прибрати місце");
            layout.add(weight, width, length, height, remove);
            layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        }

        ShipmentSeat toSeat() {
            return new ShipmentSeat(zero(weight.getValue()), zero(width.getValue()), zero(length.getValue()), zero(height.getValue()));
        }

        private static BigDecimalField field(String label) {
            var field = new BigDecimalField(label);
            field.setWidth("8rem");
            return field;
        }

        private static BigDecimal zero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }
}
