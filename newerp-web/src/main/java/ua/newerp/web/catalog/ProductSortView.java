package ua.newerp.web.catalog;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.catalog.ApplySortRequest;
import ua.newerp.application.catalog.CrossSellMetricDto;
import ua.newerp.application.catalog.ProductSortService;
import ua.newerp.application.catalog.SalesPeriod;
import ua.newerp.application.catalog.SetPinRequest;
import ua.newerp.application.catalog.SortGroupDto;
import ua.newerp.application.catalog.SortPinDto;
import ua.newerp.application.catalog.SortPreviewRow;
import ua.newerp.application.catalog.SortProductDto;
import ua.newerp.application.catalog.SortSnapshotDto;
import ua.newerp.domain.catalog.SortGroupType;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Panel;
import ua.newerp.web.ui.Search;

/** The storefront order of products by sales (ProductSort.razor): preview and write one group, pins and snapshots to roll back. */
@Route("catalog/sort")
@PageTitle("Порядок товарів — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class ProductSortView extends VerticalLayout {

    final Select<SortGroupDto> group = new Select<>();
    final IntegerField days = new IntegerField("Днів продажів");
    final DatePicker from = new DatePicker("З");
    final DatePicker to = new DatePicker("По");
    final Button previewButton = new Button("Переглянути");
    final Button recalculateAll = new Button("Перерахувати всі групи");
    final Checkbox panelOnly = new Checkbox("Рахувати лише продажі, додані через панель допродажу");
    final Div preview = new Div();
    final Button apply = new Button("Записати порядок");
    final Grid<SortPreviewRow> previewGrid = new Grid<>();
    final IntegerField pinPosition = new IntegerField("Позиція");
    final ComboBox<SortProductDto> pinProduct = new ComboBox<>("Товар");
    final Button pin = new Button();
    final Grid<SortPinDto> pins = new Grid<>();
    final Grid<SortSnapshotDto> snapshots = new Grid<>();
    List<SortPreviewRow> rows = List.of();

    private final ProductSortService service;
    private final List<SortGroupDto> groups;

    public ProductSortView(ProductSortService service) {
        this.service = service;
        this.groups = service.listGroups();
        setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        var hint = new Paragraph("Товари в групі стають за спаданням продажів за період, закріплені — на свої позиції. Порядок у товару один, "
                + "тож при перерахунку всіх груп товар отримує місце з останньої групи, де він є (теми → категорії → мітки). "
                + "Перед кожним записом зберігається знімок.");
        hint.addClassName("secondary");

        group.setLabel("Група");
        group.setItems(groups);
        group.setItemLabelGenerator(SortGroupDto::name);
        group.setMinWidth("18rem");
        group.addValueChangeListener(e -> {
            panelOnly.setVisible(isCrossSell());
            pin.setText("Закріпити в «" + e.getValue().name() + "»");
            pin.setEnabled(pinProduct.getValue() != null);
            showPreview();
        });
        days.setValue(90);
        days.setMin(1);
        days.setMaxWidth("9rem");
        from.addValueChangeListener(e -> days.setEnabled(from.isEmpty() || to.isEmpty()));
        to.addValueChangeListener(e -> days.setEnabled(from.isEmpty() || to.isEmpty()));
        from.setMaxWidth("10rem");
        to.setMaxWidth("10rem");
        previewButton.addClickListener(e -> showPreview());
        recalculateAll.addThemeVariants(ButtonVariant.LUMO_ERROR);
        recalculateAll.addClassName(LumoUtility.Margin.Start.AUTO);
        recalculateAll.addClickListener(e -> Confirm.ask("Перерахувати всі групи?",
                "Порядок усіх товарів на вітрині зміниться за продажами за вибраний період. Попередній порядок збережеться у знімку.",
                "Перерахувати", "Скасувати", this::recalculateAll));
        panelOnly.setValue(service.getCrossSellMetric().panelOnly());
        panelOnly.setVisible(false);
        panelOnly.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                setMetric(e.getValue());
            }
        });
        var filters = new FlexLayout(group, days, from, to, previewButton, recalculateAll);
        filters.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        filters.setAlignItems(FlexComponent.Alignment.BASELINE);
        filters.addClassName(LumoUtility.Gap.MEDIUM);
        var settings = new Div(filters, panelOnly);
        settings.addClassName("panel");

        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        apply.addClickListener(e -> apply());
        previewGrid.addColumn(r -> rows.indexOf(r) + 1).setHeader("Нове місце").setAutoWidth(true);
        previewGrid.addComponentColumn(r -> {
            var number = new Span("№" + r.productId());
            number.addClassNames("secondary", LumoUtility.FontSize.SMALL);
            var cell = new Span(new Span(r.name() + " "), number);
            if (r.pinned()) {
                var pinned = new Span("закріплено");
                pinned.addClassNames("status-badge", "tone-0");
                pinned.getStyle().set("margin-left", "var(--lumo-space-s)");
                cell.add(pinned);
            }
            return cell;
        }).setHeader("Товар").setFlexGrow(1);
        previewGrid.addColumn(SortPreviewRow::sales).setHeader("Продано").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        previewGrid.addColumn(SortPreviewRow::currentPosition).setHeader("Зараз").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        previewGrid.addComponentColumn(r -> {
            int index = rows.indexOf(r);
            var up = iconButton(VaadinIcon.ARROW_UP, "Вище", () -> move(index, -1));
            up.setEnabled(index > 0);
            var down = iconButton(VaadinIcon.ARROW_DOWN, "Нижче", () -> move(index, 1));
            down.setEnabled(index < rows.size() - 1);
            return new HorizontalLayout(up, down);
        }).setAutoWidth(true);
        compact(previewGrid, "У групі немає увімкнених товарів.");
        preview.setVisible(false);

        pinPosition.setValue(1);
        pinPosition.setMin(1);
        pinPosition.setMaxWidth("7rem");
        Search.bind(pinProduct, 2, service::searchProducts);
        pinProduct.setItemLabelGenerator(p -> p.name() + " (№" + p.productId() + ")");
        pinProduct.setPlaceholder("Назва");
        pinProduct.setMinWidth("18rem");
        pin.setText("Закріпити");
        pin.setEnabled(false);
        pinProduct.addValueChangeListener(e -> pin.setEnabled(e.getValue() != null && group.getValue() != null));
        pin.addClickListener(e -> pin());
        var pinRow = new HorizontalLayout(pinPosition, pinProduct, pin);
        pinRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        pins.addColumn(this::groupName).setHeader("Група").setAutoWidth(true);
        pins.addColumn(SortPinDto::position).setHeader("Позиція").setAutoWidth(true);
        pins.addColumn(p -> p.productName() + " №" + p.productId()).setHeader("Товар").setFlexGrow(1);
        pins.addComponentColumn(p -> iconButton(VaadinIcon.TRASH, "Відкріпити", () -> unpin(p))).setAutoWidth(true);
        compact(pins, "Закріплених товарів немає.");

        snapshots.addColumn(s -> Fmt.date(s.createdAt())).setHeader("Коли").setAutoWidth(true);
        snapshots.addColumn(SortSnapshotDto::note).setHeader("Перед чим").setFlexGrow(1);
        snapshots.addComponentColumn(s -> {
            var restore = new Button("Повернути", e -> Confirm.ask("Повернути порядок?",
                    "Порядок товарів стане таким, як був " + Fmt.date(s.createdAt()) + ".", "Повернути", "Скасувати", () -> restore(s)));
            restore.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return restore;
        }).setAutoWidth(true);
        compact(snapshots, "Знімків ще немає.");

        add(new H2("Порядок товарів"), hint, settings, preview,
                new Panel("Закріплені товари", secondary("Закріплений товар стоїть на своїй позиції в групі за будь-яких продажів."), pinRow,
                        pins),
                new Panel("Знімки порядку", snapshots));
        reloadLists();
    }

    SalesPeriod period() {
        return from.isEmpty() || to.isEmpty() ? new SalesPeriod(days.getValue(), null, null) : new SalesPeriod(null, from.getValue(), to.getValue());
    }

    void showPreview() {
        SortGroupDto selected = group.getValue();
        if (selected == null) {
            return;
        }
        Actions.run(null, () -> {
            rows = new ArrayList<>(service.preview(SortGroupType.values()[selected.type()], selected.key(), period()));
            previewGrid.setItems(rows);
            apply.setEnabled(!rows.isEmpty());
            var title = new H4("Новий порядок: " + selected.name());
            title.addClassName(LumoUtility.Margin.End.AUTO);
            var header = new HorizontalLayout(title, apply);
            header.setWidthFull();
            header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            preview.removeAll();
            preview.add(header, previewGrid);
            preview.addClassName("panel");
            preview.setVisible(true);
        });
    }

    /** Swaps the row with its neighbour; nothing is written until "Записати порядок". */
    void move(int index, int step) {
        var row = rows.get(index);
        rows.set(index, rows.get(index + step));
        rows.set(index + step, row);
        previewGrid.getDataProvider().refreshAll();
    }

    void apply() {
        SortGroupDto selected = group.getValue();
        Actions.run(null, () -> {
            var result = service.apply(new ApplySortRequest(selected.type(), selected.key(), rows.stream().map(SortPreviewRow::productId).toList()));
            Messages.success("Порядок записано: " + result.updated() + " товарів.");
            reloadLists();
            showPreview();
        });
    }

    void recalculateAll() {
        Actions.run(null, () -> {
            var result = service.recalculateAll(period());
            Messages.success("Перераховано: " + result.updated() + " записів порядку.");
            reloadLists();
            preview.setVisible(false);
        });
    }

    void pin() {
        SortGroupDto selected = group.getValue();
        Actions.run(null, () -> {
            service.setPin(new SetPinRequest(selected.type(), selected.key(), pinPosition.getValue() == null ? 1 : pinPosition.getValue(),
                    pinProduct.getValue().productId()));
            pinProduct.clear();
            reloadLists();
            showPreview();
        });
    }

    private void unpin(SortPinDto pinned) {
        Actions.run(null, () -> {
            service.removePin(pinned.id());
            reloadLists();
            if (preview.isVisible()) {
                showPreview();
            }
        });
    }

    private void restore(SortSnapshotDto snapshot) {
        Actions.run("Порядок повернуто.", () -> {
            service.restoreSnapshot(snapshot.id());
            if (preview.isVisible()) {
                showPreview();
            }
        });
    }

    private void setMetric(boolean value) {
        Actions.run(null, () -> {
            service.setCrossSellMetric(new CrossSellMetricDto(value));
            showPreview();
        });
    }

    private boolean isCrossSell() {
        return group.getValue() != null && group.getValue().type() == SortGroupType.CROSS_SELL.ordinal();
    }

    private String groupName(SortPinDto pinned) {
        return groups.stream().filter(g -> g.type() == pinned.type() && g.key().equals(pinned.key())).map(SortGroupDto::name).findFirst()
                .orElse(pinned.key() + " (групи вже немає)");
    }

    private void reloadLists() {
        pins.setItems(service.listPins());
        snapshots.setItems(service.listSnapshots());
    }

    private static Button iconButton(VaadinIcon icon, String label, Runnable action) {
        var button = new Button(icon.create(), e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.setAriaLabel(label);
        return button;
    }

    private static <T> void compact(Grid<T> grid, String empty) {
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.setEmptyStateText(empty);
        grid.setAllRowsVisible(true);
    }

    private static Component secondary(String text) {
        var paragraph = new Paragraph(text);
        paragraph.addClassName("secondary");
        return paragraph;
    }
}
