package ua.newerp.web.orders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.delivery.CreateInternationalShipmentRequest;
import ua.newerp.application.delivery.HsCodeDto;
import ua.newerp.application.delivery.InternationalFormDto;
import ua.newerp.application.delivery.InternationalShipmentService;
import ua.newerp.application.delivery.ShipmentDto;
import ua.newerp.domain.delivery.InternationalShipment;
import ua.newerp.domain.delivery.SeatPreset;
import ua.newerp.domain.delivery.ShipmentSeat;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Search;

/**
 * A waybill abroad (Nova Post International), the old site's "Міжнародна" tab: the recipient's address abroad, the customs
 * terms, every order line with its HS code, English name and weight, and the parcels. All checks happen on the server
 * (InternationalShipment), which lists everything missing at once.
 */
public class CreateInternationalShipmentDialog extends Dialog {

    final Select<InternationalFormDto.Country> country = new Select<>();
    final TextField lastName = new TextField("Прізвище");
    final TextField firstName = new TextField("Ім'я");
    final TextField phone = new TextField("Телефон");
    final TextField email = new TextField("Email");
    final TextField region = new TextField("Штат / область");
    final TextField city = new TextField("Населений пункт");
    final TextField street = new TextField("Вулиця");
    final TextField building = new TextField("Будинок");
    final TextField flat = new TextField("Квартира");
    final TextField postCode = new TextField("Індекс");
    final Select<String> incoterm = select("Умови поставки", "DAP", "DDP");
    final Select<String> payerFeesCustoms = select("Мито сплачує", "Recipient", "Sender");
    final Select<String> exportReason = select("Причина експорту", "Selling", "Gift", "Sample", "Return");
    final Select<String> cargoCategory = select("Категорія вантажу", "parcel", "documents");
    final BigDecimalField declaredCost = new BigDecimalField("Декларована вартість");
    final List<ItemRow> items = new ArrayList<>();
    final List<SeatRow> seats = new ArrayList<>();
    final Button create = new Button("Створити накладну");
    private final Div seatRows = new Div();
    private final InternationalShipmentService service;
    private final int orderId;
    private final int userId;
    private final Consumer<ShipmentDto> onCreated;
    private final String currency;

    public CreateInternationalShipmentDialog(InternationalShipmentService service, int orderId, int userId, Consumer<ShipmentDto> onCreated) {
        this.service = service;
        this.orderId = orderId;
        this.userId = userId;
        this.onCreated = onCreated;
        InternationalFormDto form = service.form(orderId);
        currency = form.currency();
        setHeaderTitle("Міжнародна накладна (Nova Post International)");
        setWidth("min(70rem, 100%)");
        if (!form.canCreate()) {
            add(note("Ключ API Nova Post International не задано, тож накладну створити не можна.", LumoUtility.TextColor.ERROR));
        } else if (form.test()) {
            add(note("Ключа API немає: створиться лише тестова накладна (номер на TEST), у Nova Post її не буде.", null));
        }

        InternationalShipment.Recipient r = form.recipient();
        List<InternationalFormDto.Country> countries = service.countries();
        country.setLabel("Країна");
        country.setItems(countries);
        country.setItemLabelGenerator(InternationalFormDto.Country::name);
        countries.stream().filter(c -> c.code().equals(r.countryCode())).findFirst().ifPresent(country::setValue);
        country.addValueChangeListener(e -> region.setRequiredIndicatorVisible(needsRegion()));
        lastName.setValue(r.lastName());
        firstName.setValue(r.firstName());
        phone.setValue(r.phone());
        email.setValue(r.email());
        region.setValue(r.region());
        region.setRequiredIndicatorVisible(needsRegion());
        region.setHelperText("Обов'язково для США, Ірландії, Канади");
        city.setValue(r.city());
        street.setValue(r.street());
        postCode.setValue(r.postCode());
        add(heading("Отримувач"), row(lastName, firstName, phone, email), row(country, region, city, postCode), row(street, building, flat));

        InternationalShipment.Terms terms = form.terms();
        incoterm.setValue(terms.incoterm());
        payerFeesCustoms.setValue(terms.payerFeesCustoms());
        exportReason.setValue(terms.exportReason());
        cargoCategory.setValue(terms.cargoCategory());
        declaredCost.setValue(Fmt.plain(form.declaredCost()));
        declaredCost.setSuffixComponent(new Paragraph(currency));
        add(heading("Митниця"), row(incoterm, payerFeesCustoms, exportReason, cargoCategory, declaredCost));

        add(heading("Товари"));
        var hint = note("Для кожного товару: код УКТЗЕД (почніть вводити назву — з'являться підказки), англійська назва і вага всього рядка в грамах.",
                null);
        add(hint);
        for (InternationalShipment.Item item : form.items()) {
            var row = new ItemRow(item);
            items.add(row);
            add(row.layout);
        }

        var seatButtons = new HorizontalLayout(new H4("Місця"));
        seatButtons.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        for (SeatPreset preset : form.seatPresets()) {
            seatButtons.add(new Button("+ " + preset.name(), e -> addSeat(preset.seat())));
        }
        seatButtons.add(new Button("+ Інша", e -> addSeat(new ShipmentSeat(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))));
        add(seatButtons, note("Одне місце важить стільки, скільки товари в ньому; вагу в кг беремо, лише коли місць кілька.", null), seatRows);
        if (!form.seatPresets().isEmpty()) {
            addSeat(form.seatPresets().getFirst().seat());
        }

        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setEnabled(form.canCreate());
        create.addClickListener(e -> create());
        getFooter().add(new Button("Скасувати", e -> close()), create);
    }

    void create() {
        var recipient = new InternationalShipment.Recipient(firstName.getValue(), lastName.getValue(), phone.getValue(), email.getValue(),
                country.getValue() == null ? "" : country.getValue().code(), region.getValue(), city.getValue(), street.getValue(),
                building.getValue(), flat.getValue(), postCode.getValue());
        var terms = new InternationalShipment.Terms(incoterm.getValue(), exportReason.getValue(), payerFeesCustoms.getValue(),
                cargoCategory.getValue(), null, null);
        var request = new CreateInternationalShipmentRequest(recipient, terms, items.stream().map(ItemRow::toItem).toList(),
                seats.stream().map(SeatRow::toSeat).toList(), declaredCost.getValue(), currency);
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

    private boolean needsRegion() {
        return country.getValue() != null && InternationalShipment.REGION_REQUIRED.contains(country.getValue().code());
    }

    /** An order line: the name and amount from the order, the customs fields typed in (an HS suggestion fills the English name). */
    final class ItemRow {
        final ComboBox<HsCodeDto> hsCode = new ComboBox<>("Код УКТЗЕД");
        final TextField nameEng = new TextField("Англійська назва");
        final IntegerField weight = new IntegerField("Вага, г");
        final FlexLayout layout;
        private final InternationalShipment.Item item;

        ItemRow(InternationalShipment.Item item) {
            this.item = item;
            Search.bind(hsCode, 2, text -> service.hsCodes(text, country.getValue() == null ? "" : country.getValue().code()));
            hsCode.setItemLabelGenerator(c -> c.hsCode() + " — " + c.label());
            // A code typed by hand (not found by the search) is taken as is.
            hsCode.setAllowCustomValue(true);
            hsCode.addCustomValueSetListener(e -> hsCode.setValue(new HsCodeDto(e.getDetail().strip(), "", "")));
            hsCode.addValueChangeListener(e -> {
                if (e.getValue() != null && !e.getValue().nameEng().isBlank() && nameEng.getValue().isBlank()) {
                    nameEng.setValue(e.getValue().nameEng());
                }
            });
            hsCode.setMinWidth("20rem");
            weight.setMin(1);
            weight.setWidth("8rem");
            var name = new Paragraph(item.name() + " × " + Fmt.qty(item.amount()) + ", " + Fmt.money(item.cost()));
            name.setMinWidth("16rem");
            layout = row(name, hsCode, nameEng, weight);
            layout.setAlignItems(FlexComponent.Alignment.BASELINE);
        }

        InternationalShipment.Item toItem() {
            return new InternationalShipment.Item(item.name(), nameEng.getValue(), hsCode.getValue() == null ? "" : hsCode.getValue().hsCode(),
                    weight.getValue() == null ? 0 : weight.getValue(), item.amount(), item.cost());
        }
    }

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

    private static Select<String> select(String label, String... values) {
        var select = new Select<String>();
        select.setLabel(label);
        select.setItems(values);
        return select;
    }

    private static FlexLayout row(Component... components) {
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
}
