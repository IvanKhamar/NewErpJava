package ua.newerp.web.stock;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
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
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.stock.ApplyStocktakingRequest;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockItemFilter;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StocktakingCountRequest;
import ua.newerp.application.stock.StocktakingLineDto;
import ua.newerp.application.stock.StocktakingPreviewDto;
import ua.newerp.application.stock.StocktakingResult;
import ua.newerp.application.stock.StocktakingService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/**
 * Stocktaking of consumables: type the counted quantities in, or download the sheet, fill the "Фактично" column and upload it.
 * Only counted rows change; each difference goes into the movement log.
 */
@Route("stock/stocktaking")
@PageTitle("Інвентаризація — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class StocktakingView extends VerticalLayout {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    final Select<LookupItem> category = new Select<>();
    /** Where was counted: the main stock or a warehouse; shown only when there are warehouses. */
    final Select<LookupItem> place;
    final Grid<Row> grid = new Grid<>();
    final TextField comment = new TextField("Коментар");
    final Button apply = new Button("Провести інвентаризацію");
    final Div uploadErrors = new Div();
    private final Upload upload;
    private final StockRepository repository;
    private final StocktakingService service;
    private final CurrentSession session;
    List<Row> rows = List.of();

    public StocktakingView(StockRepository repository, StocktakingService service, LocationService warehouses, CurrentSession session) {
        this.repository = repository;
        this.service = service;
        this.session = session;
        UI ui = UI.getCurrent();
        var hint = new Paragraph("Впишіть пораховану кількість у колонку «Фактично» — тут або у відомості Excel, яку потім завантажте. "
                + "Зміниться лише те, що пораховано: залишок стане фактичним, а різниця потрапить у журнал руху.");
        hint.addClassName("secondary");

        category.setLabel("Категорія");
        category.setItems(repository.listCategories());
        category.setItemLabelGenerator(c -> c == null ? "Усі" : c.name());
        category.setEmptySelectionAllowed(true);
        category.addValueChangeListener(e -> load());
        place = Places.select("Що рахуємо", warehouses);
        place.addValueChangeListener(e -> load());
        var sheet = new Anchor(DownloadHandler.fromInputStream(e -> {
            byte[] file = service.sheet(categoryId(), warehouseId());
            return new DownloadResponse(new ByteArrayInputStream(file), "inventaryzatsiia-" + LocalDate.now() + ".xlsx", XLSX,
                    file.length);
        }), "");
        sheet.add(new Button("Відомість Excel", VaadinIcon.DOWNLOAD.create()));
        upload = new Upload(UploadHandler.inMemory((metadata, data) -> ui.access(() -> uploadSheet(data))));
        upload.setAcceptedFileExtensions(".xlsx");
        upload.setMaxFileSize(StocktakingService.MAX_FILE_BYTES);
        upload.setUploadButton(new Button("Завантажити заповнену", VaadinIcon.UPLOAD.create()));
        upload.setDropAllowed(false);
        upload.addFileRejectedListener(e -> Messages.error("Файл більший за 5 МБ або не .xlsx."));
        var toolbar = new HorizontalLayout(place, category, sheet, upload);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        grid.addColumn(r -> r.item.name()).setHeader("Назва").setFlexGrow(1);
        grid.addColumn(r -> r.item.categoryName()).setHeader("Категорія").setAutoWidth(true);
        grid.addColumn(r -> Fmt.qty(r.booked) + " " + r.item.unit()).setHeader("За обліком").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        grid.addComponentColumn(r -> r.actual).setHeader("Фактично").setAutoWidth(true);
        grid.addComponentColumn(r -> r.difference).setHeader("Різниця").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Розхідників немає.");
        grid.setAllRowsVisible(true);

        comment.setPlaceholder("Наприклад, «річна, жовтень»");
        comment.setMaxLength(200);
        comment.setWidth("24rem");
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        apply.addClickListener(e -> apply());
        var footer = new HorizontalLayout(comment, apply);
        footer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        add(new H2("Інвентаризація"), hint, toolbar, uploadErrors, grid, footer);
        load();
    }

    void uploadSheet(byte[] data) {
        Actions.run(null, () -> {
            StocktakingPreviewDto preview = service.preview(data, warehouseId());
            upload.clearFileList();
            showErrors(preview.errors());
            if (!preview.errors().isEmpty()) {
                return;
            }
            // The whole list, so rows of another category in the sheet are not lost
            category.setValue(null);
            Map<Integer, Row> byId = rows.stream().collect(Collectors.toMap(r -> r.item.id(), Function.identity()));
            preview.lines().forEach(l -> {
                Row row = byId.get(l.itemId());
                if (row != null) {
                    row.actual.setValue(l.actual());
                }
            });
            long changed = preview.lines().stream().map(StocktakingLineDto::difference).filter(d -> d.signum() != 0).count();
            Messages.success("З відомості пораховано позицій: " + preview.lines().size() + ", з розбіжністю: " + changed
                    + ". Перевірте й проведіть.");
        });
    }

    void apply() {
        List<StocktakingCountRequest> counts = rows.stream().filter(r -> r.actual.getValue() != null)
                .map(r -> new StocktakingCountRequest(r.item.id(), r.actual.getValue())).toList();
        if (counts.isEmpty()) {
            Messages.warning("Впишіть фактичну кількість хоча б для однієї позиції.");
            return;
        }
        var user = session.user().orElseThrow();
        Actions.run(null, () -> {
            StocktakingResult result = service.apply(new ApplyStocktakingRequest(counts, comment.getValue(), warehouseId()),
                    user.userId(), user.username());
            Messages.success("Проведено. Пораховано позицій: " + result.counted() + ", змінено залишків: " + result.changed()
                    + (result.value().signum() == 0 ? "." : ", різниця на " + Fmt.money(result.value()) + "."));
            comment.clear();
            load();
        });
    }

    private Integer warehouseId() {
        return Places.warehouseId(place);
    }

    private Integer categoryId() {
        return category.getValue() == null ? null : category.getValue().id();
    }

    private void load() {
        Map<Integer, BigDecimal> booked = service.booked(warehouseId());
        rows = repository.listItems(new StockItemFilter(categoryId(), false)).stream()
                .map(i -> new Row(i, booked.getOrDefault(i.id(), BigDecimal.ZERO)))
                .toList();
        grid.setItems(rows);
    }

    private void showErrors(List<String> errors) {
        uploadErrors.removeAll();
        if (errors.isEmpty()) {
            return;
        }
        var list = new UnorderedList();
        errors.forEach(e -> list.add(new ListItem(e)));
        var note = new Div(new Paragraph("Відомість не прочитано, нічого не змінено:"), list);
        note.addClassNames("note", "tone-3");
        uploadErrors.add(note);
    }

    /** One item with its count being typed in; the difference updates as you type. */
    static final class Row {
        final StockItemDto item;
        /** What the books say lies at the counted place. */
        final BigDecimal booked;
        final BigDecimalField actual = new BigDecimalField();
        final Span difference = new Span();

        Row(StockItemDto item, BigDecimal booked) {
            this.item = item;
            this.booked = booked;
            actual.setWidth("8rem");
            actual.setLocale(Fmt.UK);
            actual.setSuffixComponent(new Span(item.unit()));
            actual.setValueChangeMode(ValueChangeMode.EAGER);
            actual.addValueChangeListener(e -> showDifference());
        }

        private void showDifference() {
            BigDecimal value = actual.getValue();
            difference.removeClassNames(LumoUtility.TextColor.ERROR, LumoUtility.TextColor.SUCCESS);
            if (value == null) {
                difference.setText("");
                return;
            }
            BigDecimal delta = value.subtract(booked);
            difference.setText(delta.signum() == 0 ? "збігається" : (delta.signum() > 0 ? "+" : "") + Fmt.qty(delta));
            if (delta.signum() != 0) {
                difference.addClassName(delta.signum() < 0 ? LumoUtility.TextColor.ERROR : LumoUtility.TextColor.SUCCESS);
            }
        }
    }
}
