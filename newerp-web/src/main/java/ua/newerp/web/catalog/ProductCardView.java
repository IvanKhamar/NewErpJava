package ua.newerp.web.catalog;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
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
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.catalog.ProductCardDto;
import ua.newerp.application.catalog.ProductService;
import ua.newerp.application.catalog.UpdateProductRequest;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;

/**
 * A shop product card: price, on/off, categories and labels can be changed; the stock by size is read-only (it comes from
 * production). Saving sends the dateModified the card was opened with, so a change made meanwhile in OpenCart is not overwritten.
 */
@Route("catalog/products/:id(\\d+)")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class ProductCardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    final Grid<ProductCardDto.Size> sizes = new Grid<>();
    final BigDecimalField price = new BigDecimalField("Ціна, грн");
    final Checkbox enabled = new Checkbox("Показувати на сайті");
    final MultiSelectComboBox<LookupItem> categories = new MultiSelectComboBox<>("Категорії");
    final MultiSelectComboBox<LookupItem> labels = new MultiSelectComboBox<>("Мітки");
    final Button save = new Button("Зберегти");
    private final ProductService service;
    private int id;
    ProductCardDto product;

    public ProductCardView(ProductService service) {
        this.service = service;
        price.setLocale(Fmt.UK);
        for (MultiSelectComboBox<LookupItem> box : List.of(categories, labels)) {
            box.setItemLabelGenerator(LookupItem::name);
            box.setClearButtonVisible(true);
        }
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
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
        var priceTitle = new H3(Fmt.money(product.price()));
        priceTitle.addClassName(LumoUtility.Margin.Start.AUTO);
        var header = new HorizontalLayout(new H2(product.name().isBlank() ? "Товар №" + product.id() : product.name()),
                ProductListView.state(product.enabled()), priceTitle);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var info = new FormLayout();
        info.addFormItem(new Span(String.valueOf(product.id())), "№");
        info.addFormItem(new Span(product.model()), "Модель");
        info.addFormItem(new Span(String.valueOf(product.quantity())), "Залишок усього");
        info.addFormItem(new Span(Fmt.date(product.dateModified())), "Змінено");

        price.setValue(product.price());
        enabled.setValue(product.enabled());
        List<LookupItem> allLabels = service.labels();
        labels.setItems(allLabels);
        labels.setValue(chosen(allLabels, product.labels()));
        // A category switched off later stays selectable for the product that is already in it.
        Map<Integer, LookupItem> allCategories = new LinkedHashMap<>();
        service.categories().forEach(c -> allCategories.put(c.id(), c));
        product.categories().forEach(c -> allCategories.putIfAbsent(c.id(), c));
        categories.setItems(allCategories.values());
        categories.setValue(chosen(List.copyOf(allCategories.values()), product.categories()));
        var form = new FormLayout(price, enabled, categories, labels);
        var offHint = new Paragraph("Вимкнений товар зникає з сайту, і виробництво не передає йому залишки через категорію.");
        offHint.addClassName("secondary");

        var hint = new Paragraph("Залишки за розмірами оновлює виробництво з пошитого складу; опис, фото й нові товари — в адмінці OpenCart.");
        hint.addClassName("secondary");
        sizes.setItems(product.sizes());
        add(back(), header, info, form, offHint, save, hint, sizes);
    }

    void save() {
        Actions.run("Збережено.", () -> {
            if (price.getValue() == null) {
                throw new DomainException("Вкажіть ціну.");
            }
            product = service.update(id, new UpdateProductRequest(price.getValue(), enabled.getValue(), ids(labels.getValue()),
                    ids(categories.getValue()), product.dateModified()));
            removeAll();
            render();
        });
    }

    /** The items of the list that the product has, by id (names may differ in letter case or spaces). */
    private static Set<LookupItem> chosen(List<LookupItem> items, List<LookupItem> current) {
        Set<Integer> ids = current.stream().map(LookupItem::id).collect(Collectors.toSet());
        return items.stream().filter(i -> ids.contains(i.id())).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Integer> ids(Set<LookupItem> items) {
        return items.stream().map(LookupItem::id).toList();
    }

    private static Component back() {
        var link = new RouterLink("← Товари", ProductListView.class);
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }
}
