package ua.newerp.web.catalog;

import java.util.Objects;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.catalog.PreOrderFilter;
import ua.newerp.application.catalog.PreOrderRow;
import ua.newerp.application.catalog.PreOrderService;
import ua.newerp.application.catalog.UpdatePreOrderRequest;
import ua.newerp.domain.catalog.PreOrder;
import ua.newerp.domain.catalog.SizeAvailability;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;

/** Pre-orders of sizes (PreOrders.razor): the quota and expected date per size, edited in the row. */
@Route("preorders")
@PageTitle("Передзамовлення — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class PreOrdersView extends VerticalLayout {

    final Button settle = new Button("Списати продане наперед");
    final TextField search = new TextField("Товар чи артикул");
    final Checkbox onlyWithQuota = new Checkbox("Лише з лімітом");
    final Grid<Row> grid = new Grid<>();

    private final PreOrderService service;

    public PreOrdersView(PreOrderService service) {
        this.service = service;
        settle.addClickListener(e -> settle());
        var title = new H2("Передзамовлення");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, settle);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        var hint = new Paragraph("Коли розміру немає в наявності, вітрина продає його наперед, доки не вичерпається ліміт, і показує дату, "
                + "коли розмір очікується. Продане наперед (мінусовий залишок) списується з ліміту автоматично; кнопка робить це одразу.");
        hint.addClassName("secondary");

        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setMinWidth("20rem");
        search.addValueChangeListener(e -> load());
        onlyWithQuota.addValueChangeListener(e -> load());
        var filters = new HorizontalLayout(search, onlyWithQuota);
        filters.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        grid.addComponentColumn(r -> {
            var model = new Span(r.source.model());
            model.addClassNames("secondary", LumoUtility.FontSize.SMALL, LumoUtility.Display.BLOCK);
            return new Div(new Span(r.source.productName()), model);
        }).setHeader("Товар").setFlexGrow(1);
        grid.addColumn(r -> r.source.size()).setHeader("Розмір").setAutoWidth(true);
        grid.addColumn(r -> r.source.quantity()).setHeader("Залишок").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(r -> r.quota).setHeader("Ліміт").setAutoWidth(true);
        grid.addComponentColumn(r -> r.date).setHeader("Очікується").setAutoWidth(true);
        grid.addComponentColumn(PreOrdersView::availability).setHeader("На вітрині").setAutoWidth(true);
        grid.addComponentColumn(r -> r.save).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Нічого не знайдено.");
        grid.setAllRowsVisible(true);

        add(header, hint, filters, grid);
        load();
    }

    void settle() {
        Actions.run(null, () -> {
            int sizes = service.settle().sizes();
            Messages.success(sizes == 0 ? "Списувати нічого." : "Списано з лімітів у розмірах: " + sizes + ".");
            load();
        });
    }

    void save(Row row) {
        Actions.run(row.source.productName() + ", " + row.source.size() + ": збережено.", () -> {
            service.update(row.source.productOptionValueId(),
                    new UpdatePreOrderRequest(row.quota.getValue() == null ? 0 : row.quota.getValue(), row.date.getValue()));
            load();
        });
    }

    private void load() {
        grid.setItems(service.list(new PreOrderFilter(search.getValue(), onlyWithQuota.getValue())).stream().map(Row::new).toList());
    }

    private static Div availability(Row row) {
        var availability = SizeAvailability.values()[row.source.availability()];
        var badge = new Span(switch (availability) {
            case IN_STOCK -> "в наявності";
            case PRE_ORDER -> "передзамовлення";
            default -> "немає";
        });
        badge.addClassNames("status-badge", switch (availability) {
            case IN_STOCK -> "tone-1";
            case PRE_ORDER -> "tone-2";
            default -> "tone-0";
        });
        var cell = new Div(badge);
        if (row.source.quantity() < 0) {
            var ahead = new Span("продано наперед: " + -row.source.quantity());
            ahead.addClassNames("secondary", LumoUtility.FontSize.SMALL, LumoUtility.Display.BLOCK);
            cell.add(ahead);
        }
        return cell;
    }

    /** A size being edited: Save turns on when the quota or the date differs from what is stored. */
    final class Row {
        final PreOrderRow source;
        final IntegerField quota = new IntegerField();
        final DatePicker date = new DatePicker();
        final Button save = new Button("Зберегти");

        Row(PreOrderRow source) {
            this.source = source;
            quota.setMin(0);
            quota.setMax(PreOrder.MAX_QUOTA);
            quota.setValue(source.quota());
            quota.setValueChangeMode(ValueChangeMode.EAGER);
            quota.setWidth("6rem");
            date.setValue(source.dateAvailable());
            date.setClearButtonVisible(true);
            date.setWidth("10rem");
            date.setLocale(Fmt.UK);
            save.addThemeVariants(ButtonVariant.LUMO_SMALL);
            save.setEnabled(false);
            save.addClickListener(e -> save(this));
            quota.addValueChangeListener(e -> save.setEnabled(changed()));
            date.addValueChangeListener(e -> save.setEnabled(changed()));
        }

        boolean changed() {
            return !Objects.equals(quota.getValue(), source.quota()) || !Objects.equals(date.getValue(), source.dateAvailable());
        }
    }
}
