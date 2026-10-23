package ua.newerp.web.marketing;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.marketing.ContactExportRequest;
import ua.newerp.application.marketing.MarketingService;
import ua.newerp.domain.marketing.ContactField;
import ua.newerp.domain.marketing.ContactSource;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;

/** Contacts for a mailing as CSV (ContactExport.razor): unique phones or e-mails with the name and city of the last order. */
@Route("marketing/export")
@PageTitle("Вивантаження контактів — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class ContactExportView extends VerticalLayout {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    final RadioButtonGroup<ContactField> field = new RadioButtonGroup<>();
    final Checkbox orders = new Checkbox("Замовлення", true);
    final Checkbox questions = new Checkbox("Запитання про товар", true);
    final Checkbox subscribers = new Checkbox("Підписка на розсилку", true);
    final DatePicker from = new DatePicker("З");
    final DatePicker to = new DatePicker("По");
    final Button download = new Button("Завантажити CSV", VaadinIcon.DOWNLOAD.create());

    private final MarketingService service;

    public ContactExportView(MarketingService service) {
        this.service = service;
        var hint = new Paragraph("Унікальні телефони чи e-mail клієнтів для розсилки, з ім'ям і містом з останнього замовлення. "
                + "Файл CSV відкривається в Excel. Телефони — лише українські мобільні у вигляді 380XXXXXXXXX.");
        hint.addClassName("secondary");

        field.setItems(ContactField.values());
        field.setItemLabelGenerator(f -> f == ContactField.PHONE ? "Телефони" : "E-mail");
        field.setValue(ContactField.PHONE);
        field.addValueChangeListener(e -> update());
        orders.addValueChangeListener(e -> update());
        questions.addValueChangeListener(e -> update());
        subscribers.addValueChangeListener(e -> update());

        from.setLocale(Fmt.UK);
        to.setLocale(Fmt.UK);
        download.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        var link = new Anchor(DownloadHandler.fromInputStream(e -> {
            byte[] csv = csv();
            String name = "contacts-" + (field.getValue() == ContactField.PHONE ? "phones" : "emails") + "-"
                    + LocalDateTime.now().format(FILE_TIME) + ".csv";
            return new DownloadResponse(new ByteArrayInputStream(csv), name, "text/csv; charset=utf-8", csv.length);
        }), "");
        link.add(download);

        var form = new Div(field, new H4("Звідки"), orders, questions, subscribers, new HorizontalLayout(from, to), link);
        form.addClassName("panel");
        form.setMaxWidth("36rem");
        add(new H2("Вивантаження контактів"), hint, form);
        update();
    }

    EnumSet<ContactSource> sources() {
        var sources = EnumSet.noneOf(ContactSource.class);
        if (orders.getValue()) {
            sources.add(ContactSource.ORDERS);
        }
        if (field.getValue() == ContactField.PHONE && questions.getValue()) {
            sources.add(ContactSource.QUESTIONS);
        }
        if (field.getValue() == ContactField.EMAIL && subscribers.getValue()) {
            sources.add(ContactSource.SUBSCRIBERS);
        }
        return sources;
    }

    /** The file as the API gives it: UTF-8 with a BOM, which Excel needs to read Cyrillic. */
    byte[] csv() {
        byte[] csv = service.exportCsv(new ContactExportRequest(field.getValue(), sources(), from.getValue(), to.getValue()))
                .getBytes(StandardCharsets.UTF_8);
        var body = new byte[UTF8_BOM.length + csv.length];
        System.arraycopy(UTF8_BOM, 0, body, 0, UTF8_BOM.length);
        System.arraycopy(csv, 0, body, UTF8_BOM.length, csv.length);
        return body;
    }

    private void update() {
        questions.setVisible(field.getValue() == ContactField.PHONE);
        subscribers.setVisible(field.getValue() == ContactField.EMAIL);
        download.setEnabled(!sources().isEmpty());
    }
}
