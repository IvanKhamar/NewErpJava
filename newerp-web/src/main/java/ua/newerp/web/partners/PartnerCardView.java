package ua.newerp.web.partners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.partners.CategoryMapRow;
import ua.newerp.application.partners.PartnerDto;
import ua.newerp.application.partners.PartnerProductPickDto;
import ua.newerp.application.partners.PartnerService;
import ua.newerp.application.partners.ProductMapRow;
import ua.newerp.application.partners.SavePartnerRequest;
import ua.newerp.application.partners.ShopCategoryDto;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Panel;
import ua.newerp.web.ui.Search;

/** A new partner or one partner's settings with its category and product mapping (PartnerCard.razor). */
@Route("partners/:id(new|\\d+)")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class PartnerCardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    static final String NEW = "partners/new";

    final TextField name = new TextField("Назва");
    final TextField code = new TextField("Код (латиницею)");
    final TextField vendorName = new TextField("Бренд у фіді");
    final IntegerField defaultQuantity = new IntegerField("Кількість у фіді");
    final TextField feedUrl = new TextField("Адреса фіда в партнера (довідково)");
    final Checkbox feedEnabled = new Checkbox("Фід увімкнено");
    final Checkbox isActive = new Checkbox("Партнер активний");
    final TextArea notes = new TextArea("Нотатки");
    final Button save = new Button("Зберегти", VaadinIcon.CHECK.create());
    final Button delete = new Button("Видалити", VaadinIcon.TRASH.create());
    final Button addCategory = new Button("Додати категорію", VaadinIcon.PLUS.create());
    final ComboBox<PartnerProductPickDto> product = new ComboBox<>("Додати товар");
    final List<CategoryRow> categoryRows = new ArrayList<>();
    final List<ProductRow> productRows = new ArrayList<>();
    final Grid<CategoryRow> categoryGrid = new Grid<>();
    final Grid<ProductRow> productGrid = new Grid<>();
    final Div feedLink = new Div();

    private final PartnerService service;
    private List<ShopCategoryDto> categories;
    private Integer id;
    private PartnerDto partner;

    public PartnerCardView(PartnerService service) {
        this.service = service;
        setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH); // the panels take the page's width, wide grids scroll inside
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delete.addClickListener(e -> Confirm.ask("Видалити партнера?",
                "Партнера «" + name.getValue() + "» і всі його відповідності буде видалено, а фід перестане відкриватися.", "Видалити",
                "Скасувати", this::delete));
        code.setMaxWidth("12rem");
        vendorName.setMaxWidth("12rem");
        defaultQuantity.setMin(0);
        defaultQuantity.setMaxWidth("10rem");
        feedUrl.setMinWidth("24rem");
        notes.setWidthFull();

        addCategory.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        addCategory.addClickListener(e -> {
            categoryRows.add(new CategoryRow(null));
            categoryGrid.setItems(categoryRows);
        });
        categoryGrid.addColumn(r -> r.id == null ? "нова" : String.valueOf(r.id)).setHeader("№ у фіді").setAutoWidth(true);
        categoryGrid.addComponentColumn(r -> r.category).setHeader("Категорія магазину").setAutoWidth(true);
        categoryGrid.addComponentColumn(r -> r.size).setHeader("Розмір").setAutoWidth(true);
        categoryGrid.addComponentColumn(r -> r.externalId).setHeader("Id у партнера").setAutoWidth(true);
        categoryGrid.addComponentColumn(r -> r.externalName).setHeader("Назва в партнера").setAutoWidth(true);
        categoryGrid.addComponentColumn(r -> r.percentSupply).setHeader("Націнка, %").setAutoWidth(true);
        categoryGrid.addComponentColumn(r -> r.percentSale).setHeader("Стара ціна, +%").setAutoWidth(true);
        categoryGrid.addComponentColumn(r -> removeButton("Прибрати рядок", () -> {
            categoryRows.remove(r);
            categoryGrid.setItems(categoryRows);
        })).setAutoWidth(true);
        setUp(categoryGrid, "Категорій ще немає.");

        Search.bind(product, 2, service::searchProducts);
        product.setItemLabelGenerator(p -> p.name() + " (№" + p.id() + ")");
        product.setPlaceholder("Назва чи №");
        product.setWidth("28rem");
        product.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                addProduct(e.getValue());
                product.clear();
            }
        });
        productGrid.addComponentColumn(r -> {
            var number = new Span("№" + r.productId);
            number.addClassNames("secondary", LumoUtility.FontSize.SMALL, LumoUtility.Display.BLOCK);
            var cell = new Div(new Span(r.productName), number);
            if (!r.productEnabled) {
                var off = new Span("вимкнено, у фід не йде");
                off.addClassNames("status-badge", "tone-2");
                cell.add(off);
            }
            return cell;
        }).setHeader("Товар").setAutoWidth(true);
        productGrid.addComponentColumn(r -> r.size).setHeader("Розмір").setAutoWidth(true);
        productGrid.addComponentColumn(r -> r.externalId).setHeader("Артикул у партнера").setAutoWidth(true);
        productGrid.addComponentColumn(r -> r.externalName).setHeader("Назва в партнера").setAutoWidth(true);
        productGrid.addComponentColumn(r -> r.priceSupply).setHeader("Ціна, грн").setAutoWidth(true);
        productGrid.addComponentColumn(r -> r.priceSale).setHeader("Стара ціна, грн").setAutoWidth(true);
        productGrid.addComponentColumn(r -> removeButton("Прибрати товар", () -> {
            productRows.remove(r);
            productGrid.setItems(productRows);
        })).setAutoWidth(true);
        setUp(productGrid, "Окремих товарів немає.");
    }

    static String path(int partnerId) {
        return "partners/" + partnerId;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String param = event.getRouteParameters().get("id").orElse("new");
        open("new".equals(param) ? null : Integer.valueOf(param));
    }

    @Override
    public String getPageTitle() {
        return (id == null ? "Новий партнер" : partner == null ? "Партнер" : partner.name()) + " — NewErp";
    }

    void open(Integer partnerId) {
        id = partnerId;
        removeAll();
        if (categories == null) {
            categories = service.listCategories();
        }
        partner = id == null ? null : service.get(id).orElse(null);
        if (id != null && partner == null) {
            add(back(), new Paragraph("Партнера №" + id + " не знайдено."));
            return;
        }
        load();

        var title = new H2(partner == null ? "Новий партнер" : partner.name());
        title.addClassName(LumoUtility.Margin.End.AUTO);
        delete.setVisible(partner != null);
        var header = new HorizontalLayout(title, delete, save);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var settings = new Panel("Налаштування", wrap(name, code, vendorName, defaultQuantity), wrap(feedUrl, feedEnabled, isActive), notes,
                feedLink);
        var categoryHint = secondary("Усі увімкнені товари категорії потрапляють у фід, кожен розмір окремо. Ціна — ціна розміру плюс націнка, "
                + "стара ціна — ціна розміру плюс свій відсоток. Рядок з розміром важливіший за рядок усієї категорії.");
        var categoryPanel = new Panel("Категорії", categoryHint, categoryGrid, addCategory);
        var productHint = secondary("Товари з власним артикулом, назвою чи ціною для партнера. Порожня ціна — ціна з магазину. "
                + "Такий товар не дублюється пропозицією з категорії.");
        var productPanel = new Panel("Окремі товари", productHint, product, productGrid);
        add(back(), header, settings, categoryPanel, productPanel);
    }

    private void load() {
        name.setValue(partner == null ? "" : partner.name());
        code.setValue(partner == null ? "" : partner.code());
        vendorName.setValue(partner == null ? "Vidro" : partner.vendorName());
        defaultQuantity.setValue(partner == null ? 5 : partner.defaultQuantity());
        feedUrl.setValue(partner == null ? "" : partner.feedUrl());
        feedEnabled.setValue(partner != null && partner.feedEnabled());
        isActive.setValue(partner == null || partner.isActive());
        notes.setValue(partner == null ? "" : partner.notes());
        feedLink.removeAll();
        if (partner != null && partner.feedEnabled()) {
            String url = feedUrl(partner.code());
            var link = new Anchor(url, url, AnchorTarget.BLANK);
            feedLink.add(new Paragraph(new Span("Фід: "), link));
        }

        categoryRows.clear();
        productRows.clear();
        if (partner != null) {
            partner.categoryMap().forEach(m -> {
                var row = new CategoryRow(m.id());
                row.category.setValue(categories.stream().filter(c -> c.id() == m.categoryId()).findFirst().orElse(null));
                row.sizes(m.sizeName() == null ? "" : m.sizeName());
                row.externalId.setValue(Objects.toString(m.externalId(), ""));
                row.externalName.setValue(Objects.toString(m.externalName(), ""));
                row.percentSupply.setValue(m.percentSupply());
                row.percentSale.setValue(m.percentSale());
                categoryRows.add(row);
            });
            partner.productMap().forEach(m -> {
                var row = new ProductRow(m.productId(), m.productName(), m.productEnabled(), null);
                row.size.setValue(m.sizeName() == null ? "" : m.sizeName());
                row.externalId.setValue(Objects.toString(m.externalId(), ""));
                row.externalName.setValue(Objects.toString(m.externalName(), ""));
                row.priceSupply.setValue(m.priceSupply());
                row.priceSale.setValue(m.priceSale());
                productRows.add(row);
            });
        }
        categoryGrid.setItems(categoryRows);
        productGrid.setItems(productRows);
    }

    void addProduct(PartnerProductPickDto picked) {
        String sizes = picked.sizes().stream()
                .map(s -> s.name().isEmpty() ? Fmt.money(s.price()) : s.name() + " — " + Fmt.money(s.price()))
                .collect(Collectors.joining(", "));
        productRows.add(new ProductRow(picked.id(), picked.name(), picked.isEnabled(), sizes));
        productGrid.setItems(productRows);
    }

    SavePartnerRequest request() {
        return new SavePartnerRequest(code.getValue(), name.getValue(), isActive.getValue(), feedUrl.getValue(), feedEnabled.getValue(),
                vendorName.getValue(), notes.getValue(), defaultQuantity.getValue(),
                categoryRows.stream().map(r -> new CategoryMapRow(r.category.getValue() == null ? 0 : r.category.getValue().id(),
                        r.size.getValue(), r.externalId.getValue(), r.externalName.getValue(), r.percentSupply.getValue(),
                        r.percentSale.getValue())).toList(),
                productRows.stream().map(r -> new ProductMapRow(r.productId, r.size.getValue(), r.externalId.getValue(),
                        r.externalName.getValue(), r.priceSupply.getValue(), r.priceSale.getValue())).toList());
    }

    void save() {
        var request = request();
        if (id == null) {
            Actions.run("Партнера додано.", () -> {
                int created = service.create(request);
                getUI().ifPresent(ui -> ui.navigate(path(created)));
            });
        } else {
            Actions.run("Партнера збережено.", () -> {
                service.update(id, request);
                open(id);
            });
        }
    }

    void delete() {
        Actions.run(null, () -> {
            service.delete(id);
            Messages.success("Партнера видалено.");
            getUI().ifPresent(ui -> ui.navigate("partners"));
        });
    }

    /** The public feed address: the server's root (not the UI's /ui/) plus /feeds/code.xml. */
    static String feedUrl(String code) {
        String path = "/feeds/" + code + ".xml";
        var request = VaadinServletRequest.getCurrent();
        return request == null ? path : ServletUriComponentsBuilder.fromContextPath(request.getHttpServletRequest()).path(path).toUriString();
    }

    /** One row of the category mapping; the size list follows the chosen category. */
    final class CategoryRow {
        final Integer id;
        final ComboBox<ShopCategoryDto> category = new ComboBox<>();
        final Select<String> size = new Select<>();
        final TextField externalId = small("8rem");
        final TextField externalName = small("12rem");
        final BigDecimalField percentSupply = number();
        final BigDecimalField percentSale = number();

        CategoryRow(Integer id) {
            this.id = id;
            category.setItems(categories);
            category.setItemLabelGenerator(ShopCategoryDto::name);
            category.setPlaceholder("—");
            category.setWidth("14rem");
            size.setItemLabelGenerator(s -> s.isEmpty() ? "усі" : s);
            size.setWidth("8rem");
            sizes("");
            category.addValueChangeListener(e -> sizes(""));
        }

        /** Fills the sizes of the chosen category; a saved size no longer in the category stays in the list so it is not lost silently. */
        void sizes(String selected) {
            var items = new ArrayList<String>();
            items.add("");
            if (category.getValue() != null) {
                items.addAll(category.getValue().sizes());
            }
            if (!items.contains(selected)) {
                items.add(selected);
            }
            size.setItems(items);
            size.setValue(selected);
        }
    }

    /** One product with its own article, name or price for the partner. */
    static final class ProductRow {
        final int productId;
        final String productName;
        final boolean productEnabled;
        final TextField size = small("8rem");
        final TextField externalId = small("8rem");
        final TextField externalName = small("12rem");
        final BigDecimalField priceSupply = number();
        final BigDecimalField priceSale = number();

        ProductRow(int productId, String productName, boolean productEnabled, String sizesHint) {
            this.productId = productId;
            this.productName = productName;
            this.productEnabled = productEnabled;
            size.setPlaceholder("усі");
            size.setHelperText(sizesHint);
        }
    }

    private static TextField small(String width) {
        var field = new TextField();
        field.setWidth(width);
        return field;
    }

    private static BigDecimalField number() {
        var field = new BigDecimalField();
        field.setWidth("7rem");
        return field;
    }

    private static <T> void setUp(Grid<T> grid, String empty) {
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.setEmptyStateText(empty);
        grid.setAllRowsVisible(true);
    }

    private static Button removeButton(String label, Runnable action) {
        var button = new Button(VaadinIcon.TRASH.create(), e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.setAriaLabel(label);
        return button;
    }

    private static FlexLayout wrap(Component... components) {
        var row = new FlexLayout(components);
        row.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.addClassName(LumoUtility.Gap.MEDIUM);
        return row;
    }

    private static Paragraph secondary(String text) {
        var paragraph = new Paragraph(text);
        paragraph.addClassName("secondary");
        return paragraph;
    }

    private static Component back() {
        var link = new Anchor("partners", "← Партнери");
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }
}
