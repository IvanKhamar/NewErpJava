package ua.newerp.web.partners;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.partners.PartnerListItemDto;
import ua.newerp.application.partners.PartnerService;
import ua.newerp.web.Access;

/** Reseller partners (Partners.razor): who gets the shop's XML feed. */
@Route("partners")
@PageTitle("Партнери — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class PartnerListView extends VerticalLayout {

    final Grid<PartnerListItemDto> grid = new Grid<>();
    final Button create = new Button("Новий партнер", VaadinIcon.PLUS.create());

    public PartnerListView(PartnerService service) {
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(PartnerCardView.NEW)));
        var title = new H2("Партнери");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, create);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        var hint = new Paragraph("Перепродавці, яким магазин віддає XML-фід товарів з їхніми категоріями й цінами. "
                + "Фід увімкненого партнера доступний без входу за адресою /feeds/код.xml.");
        hint.addClassName("secondary");

        grid.addComponentColumn(p -> {
            var name = new Span(p.name());
            if (!p.isActive()) {
                var off = new Span("вимкнено");
                off.addClassName("status-badge");
                off.getStyle().set("margin-left", "var(--lumo-space-s)");
                return new Span(name, off);
            }
            return name;
        }).setHeader("Партнер").setFlexGrow(1);
        grid.addColumn(PartnerListItemDto::code).setHeader("Код").setAutoWidth(true);
        grid.addColumn(PartnerListItemDto::vendorName).setHeader("Бренд у фіді").setAutoWidth(true);
        grid.addColumn(PartnerListItemDto::categories).setHeader("Категорій").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(PartnerListItemDto::products).setHeader("Окремих товарів").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(p -> {
            var feed = new Span(p.feedEnabled() ? "увімкнено" : "вимкнено");
            feed.addClassNames("status-badge", p.feedEnabled() ? "tone-1" : "tone-0");
            return feed;
        }).setHeader("Фід").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Партнерів ще немає.");
        grid.setAllRowsVisible(true);
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(PartnerCardView.path(e.getItem().id()))));
        grid.setItems(service.list());

        add(header, hint, grid);
    }
}
