package ua.newerp.web.returns;

import java.util.List;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.returns.ReturnFilter;
import ua.newerp.application.returns.ReturnListItem;
import ua.newerp.application.returns.ReturnLookupItem;
import ua.newerp.application.returns.ReturnService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Paging;

/** Product returns (Returns.razor): made from the order card, plus the requests customers left on the site. */
@Route("returns")
@PageTitle("Повернення — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class ReturnListView extends VerticalLayout {

    static final int PAGE_SIZE = ReturnFilter.DEFAULT_PAGE_SIZE;

    final TextField search = new TextField();
    final Select<ReturnLookupItem> status = new Select<>();
    final Grid<ReturnListItem> grid = new Grid<>(ReturnListItem.class, false);

    private final ReturnService returns;

    public ReturnListView(ReturnService returns) {
        this.returns = returns;
        setSizeFull();
        var hint = new Paragraph("Повернення оформлюють із картки замовлення (кнопка «Повернення»). Сюди ж потрапляють заявки, "
                + "які клієнти лишили на сайті.");
        hint.addClassName("secondary");

        search.setPlaceholder("Клієнт, телефон, товар");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setMinWidth("240px");
        status.setLabel("Статус");
        status.setItems(returns.lookups().statuses());
        status.setItemLabelGenerator(s -> s == null ? "Усі" : s.name());
        status.setEmptySelectionAllowed(true);
        var filters = new HorizontalLayout(search, status);
        filters.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        grid.addColumn(ReturnListItem::id).setHeader("№").setAutoWidth(true);
        grid.addColumn(r -> Fmt.date(r.dateAdded())).setHeader("Дата").setAutoWidth(true);
        grid.addColumn(r -> "#" + r.orderId()).setHeader("Замовлення").setAutoWidth(true);
        grid.addComponentColumn(r -> twoLines(r.customer(), r.telephone())).setHeader("Клієнт").setFlexGrow(1);
        grid.addComponentColumn(r -> twoLines(r.product(), r.model())).setHeader("Товар").setFlexGrow(1);
        grid.addColumn(ReturnListItem::quantity).setHeader("К-сть").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(r -> r.statusName() == null ? "статус " + r.statusId() : r.statusName()).setHeader("Статус").setAutoWidth(true);
        grid.addColumn(ReturnListItem::actionName).setHeader("Дія").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setPageSize(PAGE_SIZE);
        grid.setEmptyStateText("Повернень не знайдено.");
        grid.setSizeFull();
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(ReturnCardView.class,
                new RouteParameters("id", String.valueOf(e.getItem().id())))));
        grid.setItems(query -> fetch(query.getOffset(), query.getLimit()).stream(),
                query -> returns.list(filter(1, 1)).totalCount());

        search.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        status.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        add(new H2("Повернення"), hint, filters, grid);
        setFlexGrow(1, grid);
    }

    List<ReturnListItem> fetch(int offset, int limit) {
        return Paging.rows(offset, limit, PAGE_SIZE, page -> returns.list(filter(page, PAGE_SIZE)).items());
    }

    ReturnFilter filter(int page, int pageSize) {
        return new ReturnFilter(null, null, status.getValue() == null ? null : status.getValue().id(), search.getValue(), page, pageSize);
    }

    private static Div twoLines(String main, String secondary) {
        var second = new Div(new Span(secondary == null ? "" : secondary));
        second.addClassName("secondary");
        return new Div(new Div(new Span(main == null ? "" : main)), second);
    }
}
