package ua.newerp.web.catalog;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.catalog.CrossSellItemDto;
import ua.newerp.application.catalog.CrossSellService;
import ua.newerp.application.catalog.ProductSortService;
import ua.newerp.application.catalog.SaveCrossSellItemRequest;
import ua.newerp.application.catalog.SortProductDto;
import ua.newerp.application.common.LookupItem;
import ua.newerp.domain.catalog.CrossSellItem;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Search;

/** What the checkout cross-sell panel offers: whole categories and single products, each with its discount. */
@Route("catalog/crosssell")
@PageTitle("Панель допродажу — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class CrossSellView extends VerticalLayout {

    final RadioButtonGroup<String> type = new RadioButtonGroup<>("Що додати");
    final ComboBox<LookupItem> category = new ComboBox<>("Категорія");
    final ComboBox<SortProductDto> product = new ComboBox<>("Товар");
    final BigDecimalField discount = new BigDecimalField("Знижка, %");
    final Button add = new Button("Додати", VaadinIcon.PLUS.create());
    final Grid<CrossSellItemDto> grid = new Grid<>();
    private final CrossSellService service;
    List<CrossSellItemDto> items = List.of();

    public CrossSellView(CrossSellService service, ProductSortService products) {
        this.service = service;
        var hint = new Paragraph("Ці товари покупець бачить у панелі допродажу при оформленні замовлення, зі знижкою рядка. "
                + "Категорія приносить усі свої увімкнені товари. Порядок товарів у панелі — на сторінці «Порядок товарів».");
        hint.addClassName("secondary");

        type.setItems(CrossSellItem.CATEGORY, CrossSellItem.PRODUCT);
        type.setItemLabelGenerator(t -> CrossSellItem.CATEGORY.equals(t) ? "категорію" : "товар");
        type.addValueChangeListener(e -> {
            category.setVisible(CrossSellItem.CATEGORY.equals(e.getValue()));
            product.setVisible(CrossSellItem.PRODUCT.equals(e.getValue()));
        });
        category.setItems(service.categories());
        category.setItemLabelGenerator(LookupItem::name);
        category.setMinWidth("18rem");
        Search.bind(product, 2, products::searchProducts);
        product.setItemLabelGenerator(p -> p.name() + " (№" + p.productId() + ")");
        product.setPlaceholder("Назва");
        product.setMinWidth("18rem");
        discount.setLocale(Fmt.UK);
        discount.setWidth("8rem");
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(e -> add());
        type.setValue(CrossSellItem.CATEGORY);
        var form = new HorizontalLayout(type, category, product, discount, add);
        form.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        form.setWrap(true);

        grid.addColumn(i -> CrossSellItem.CATEGORY.equals(i.type()) ? "Категорія" : "Товар").setHeader("Що").setAutoWidth(true);
        grid.addColumn(i -> i.name().isEmpty() ? "№" + i.targetId() + " (не знайдено)" : i.name()).setHeader("Назва")
                .setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(i -> CrossSellItem.CATEGORY.equals(i.type()) ? String.valueOf(i.products()) : (i.products() > 0 ? "" : "вимкнено"))
                .setHeader("Товарів").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(this::discountField).setHeader("Знижка, %").setAutoWidth(true);
        grid.addComponentColumn(i -> {
            var remove = new Button(VaadinIcon.TRASH.create(), e -> Confirm.ask("Прибрати з панелі?", "«" + i.name() + "» зникне з панелі допродажу.",
                    "Прибрати", "Ні", () -> remove(i)));
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            remove.setAriaLabel("Прибрати");
            return remove;
        }).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText("Панель допродажу порожня.");
        grid.setAllRowsVisible(true);

        add(new H2("Панель допродажу"), hint, form, grid);
        load();
    }

    void add() {
        boolean isCategory = CrossSellItem.CATEGORY.equals(type.getValue());
        Integer targetId = isCategory ? (category.getValue() == null ? null : category.getValue().id())
                : (product.getValue() == null ? null : product.getValue().productId());
        if (targetId == null) {
            Messages.warning(isCategory ? "Оберіть категорію." : "Оберіть товар.");
            return;
        }
        Actions.run("Додано в панель допродажу.", () -> {
            service.add(new SaveCrossSellItemRequest(type.getValue(), targetId, discount.getValue()));
            category.clear();
            product.clear();
            discount.clear();
            load();
        });
    }

    void remove(CrossSellItemDto item) {
        Actions.run("Прибрано з панелі.", () -> {
            service.delete(item.id());
            load();
        });
    }

    void setDiscount(CrossSellItemDto item, BigDecimal value) {
        Actions.run("Знижку змінено.", () -> {
            service.setDiscount(item.id(), value);
            load();
        });
    }

    private BigDecimalField discountField(CrossSellItemDto item) {
        var field = new BigDecimalField();
        field.setLocale(Fmt.UK);
        field.setWidth("7rem");
        field.setValue(Fmt.plain(item.discount()));
        field.setAriaLabel("Знижка, " + item.name());
        field.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                setDiscount(item, e.getValue());
            }
        });
        return field;
    }

    private void load() {
        items = service.list();
        grid.setItems(items);
    }
}
