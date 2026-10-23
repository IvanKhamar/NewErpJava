package ua.newerp.web.locations;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationImportResult;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.locations.SaveLocationRequest;
import ua.newerp.web.Access;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;

/**
 * Warehouses and pick-up points (Locations.razor): the shop's addresses and contacts, edited in a side form, with an Excel
 * export and import in the old site's columns.
 */
@Route("locations")
@PageTitle("Склади й пункти видачі — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class LocationsView extends VerticalLayout {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    final TextField search = new TextField();
    final Grid<LocationDto> grid = new Grid<>();
    final H3 formTitle = new H3();
    final TextField name = field("Назва", 64);
    final TextField type = field("Тип (склад, пункт видачі…)", 64);
    final TextField city = field("Місто", 128);
    final TextField address = field("Адреса", 255);
    final TextField contact = field("Контактна особа", 128);
    final TextField telephone = field("Телефон", 255);
    final TextArea description = new TextArea("Опис");
    final Button save = new Button("Зберегти");
    final Button newEntry = new Button("Новий");
    final Button delete = new Button("Видалити");
    final Div importErrors = new Div();
    private final LocationService service;
    private final Upload upload;
    Integer editId;

    public LocationsView(LocationService service) {
        this.service = service;
        UI ui = UI.getCurrent();
        var hint = new Paragraph("Довідник адрес і контактів магазину. Файл Excel має ті самі колонки, що й вивантаження старого сайту: "
                + "рядок з наявним ID оновлює запис, решта додаються.");
        hint.addClassName("secondary");

        search.setPlaceholder("Назва, місто, адреса, тип");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setMinWidth("16rem");
        search.addValueChangeListener(e -> load());
        search.addClassName(LumoUtility.Margin.End.AUTO);

        var export = new Anchor(DownloadHandler.fromInputStream(e -> {
            byte[] file = service.export();
            return new DownloadResponse(new ByteArrayInputStream(file), "sklady-" + LocalDate.now() + ".xlsx", XLSX, file.length);
        }), "");
        export.add(new Button("Вивантажити в Excel", VaadinIcon.DOWNLOAD.create()));
        upload = new Upload(UploadHandler.inMemory((metadata, data) -> ui.access(() -> importFile(data))));
        upload.setAcceptedFileExtensions(".xlsx");
        upload.setMaxFileSize(LocationService.MAX_IMPORT_BYTES);
        upload.setUploadButton(new Button("Імпорт з Excel", VaadinIcon.UPLOAD.create()));
        upload.setDropAllowed(false);
        upload.addFileRejectedListener(e -> Messages.error("Файл більший за 5 МБ або не .xlsx."));
        var toolbar = new HorizontalLayout(search, export, upload);
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.START);

        grid.addColumn(LocationDto::id).setHeader("ID").setAutoWidth(true);
        grid.addColumn(LocationDto::name).setHeader("Назва").setAutoWidth(true);
        grid.addColumn(LocationDto::type).setHeader("Тип").setAutoWidth(true);
        grid.addColumn(l -> l.city() + (l.address().isEmpty() ? "" : ", " + l.address())).setHeader("Місто й адреса").setFlexGrow(1);
        grid.addComponentColumn(l -> {
            var phone = new Span(l.telephone());
            phone.addClassNames("secondary", LumoUtility.FontSize.SMALL, LumoUtility.Display.BLOCK);
            return new Div(new Span(l.contact()), phone);
        }).setHeader("Контакт").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Записів немає.");
        grid.setAllRowsVisible(true);
        grid.addItemClickListener(e -> edit(e.getItem()));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        newEntry.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        newEntry.addClickListener(e -> edit(null));
        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delete.addClickListener(e -> Confirm.ask("Видалити запис?", "«" + name.getValue() + "» буде видалено з довідника.", "Видалити",
                "Скасувати", this::delete));
        var form = new FormLayout(name, type, city, address, contact, telephone, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        var buttons = new HorizontalLayout(save, newEntry, delete);
        var side = new Div(formTitle, form, buttons);
        side.addClassName("panel");

        var columns = new Div(new Div(grid), side);
        columns.addClassName("dashboard-columns");
        add(new H2("Склади й пункти видачі"), hint, toolbar, importErrors, columns);
        edit(null);
        load();
    }

    void edit(LocationDto location) {
        editId = location == null ? null : location.id();
        formTitle.setText(location == null ? "Новий запис" : "Запис №" + location.id());
        name.setValue(location == null ? "" : location.name());
        type.setValue(location == null ? "" : location.type());
        city.setValue(location == null ? "" : location.city());
        address.setValue(location == null ? "" : location.address());
        contact.setValue(location == null ? "" : location.contact());
        telephone.setValue(location == null ? "" : location.telephone());
        description.setValue(location == null ? "" : location.description());
        newEntry.setVisible(location != null);
        delete.setVisible(location != null);
    }

    void save() {
        var request = new SaveLocationRequest(name.getValue(), description.getValue(), contact.getValue(), telephone.getValue(),
                city.getValue(), address.getValue(), type.getValue());
        Actions.run("Збережено.", () -> {
            if (editId == null) {
                editId = service.create(request);
                formTitle.setText("Запис №" + editId);
                newEntry.setVisible(true);
                delete.setVisible(true);
            } else {
                service.update(editId, request);
            }
            load();
        });
    }

    void delete() {
        int id = editId;
        Actions.run(null, () -> {
            service.delete(id);
            edit(null);
            load();
        });
    }

    void importFile(byte[] data) {
        Actions.run(null, () -> {
            LocationImportResult result = service.importFile(data);
            upload.clearFileList();
            showErrors(result.errors());
            if (result.errors().isEmpty()) {
                Messages.success("Імпортовано: додано " + result.added() + ", оновлено " + result.updated() + ".");
            }
            load();
        });
    }

    private void showErrors(List<String> errors) {
        importErrors.removeAll();
        if (errors.isEmpty()) {
            return;
        }
        var list = new UnorderedList();
        errors.forEach(e -> list.add(new ListItem(e)));
        var note = new Div(new Paragraph("Файл не імпортовано, нічого не змінено:"), list);
        note.addClassNames("note", "tone-3");
        importErrors.add(note);
    }

    private void load() {
        grid.setItems(service.list(search.getValue()));
    }

    private static TextField field(String label, int maxLength) {
        var field = new TextField(label);
        field.setMaxLength(maxLength);
        return field;
    }
}
