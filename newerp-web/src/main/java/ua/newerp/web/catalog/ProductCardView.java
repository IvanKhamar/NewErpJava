package ua.newerp.web.catalog;

import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.catalog.ProductCardDto;
import ua.newerp.application.catalog.ProductService;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;

/** A shop product card: price, state, labels, categories and the stock by size (read-only, it comes from production). */
@Route("catalog/products/:id(\\d+)")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class ProductCardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    final Grid<ProductCardDto.Size> sizes = new Grid<>();
    private final ProductService service;
    private int id;
    ProductCardDto product;

    public ProductCardView(ProductService service) {
        this.service = service;
        sizes.addColumn(ProductCardDto.Size::name).setHeader("Розмір").setFlexGrow(1);
        sizes.addColumn(ProductCardDto.Size::quantity).setHeader("Залишок").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        sizes.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        sizes.setAllRowsVisible(true);
        sizes.setEmptyStateText("У товару немає розмірів.");
        sizes.setMaxWidth("24rem");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        open(event.getRouteParameters().getInteger("id").orElse(0));
    }

    void open(int productId) {
        id = productId;
        removeAll();
        try {
            product = service.get(id);
        } catch (NotFoundException e) {
            product = null;
            add(back(), new Paragraph("Товар №" + id + " не знайдено."));
            return;
        }
        render();
    }

    @Override
    public String getPageTitle() {
        return (product == null ? "Товар №" + id : product.name()) + " — NewErp";
    }

    private void render() {
        var price = new H3(Fmt.money(product.price()));
        price.addClassName(LumoUtility.Margin.Start.AUTO);
        var header = new HorizontalLayout(new H2(product.name().isBlank() ? "Товар №" + product.id() : product.name()),
                ProductListView.state(product.enabled()), price);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var info = new FormLayout();
        info.addFormItem(new Span(String.valueOf(product.id())), "№");
        info.addFormItem(new Span(product.model()), "Модель");
        info.addFormItem(new Span(names(product.categories())), "Категорії");
        info.addFormItem(new Span(names(product.labels())), "Мітки");
        info.addFormItem(new Span(String.valueOf(product.quantity())), "Залишок усього");
        info.addFormItem(new Span(Fmt.date(product.dateModified())), "Змінено");

        var hint = new Paragraph("Залишки за розмірами оновлює виробництво з пошитого складу; опис, фото й нові товари — в адмінці OpenCart.");
        hint.addClassName("secondary");
        sizes.setItems(product.sizes());
        add(back(), header, info, hint, sizes);
    }

    static String names(List<LookupItem> items) {
        return items.isEmpty() ? "—" : items.stream().map(LookupItem::name).collect(Collectors.joining(", "));
    }

    private static Component back() {
        var link = new RouterLink("← Товари", ProductListView.class);
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }
}
