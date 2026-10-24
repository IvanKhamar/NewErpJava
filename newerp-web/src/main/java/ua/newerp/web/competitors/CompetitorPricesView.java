package ua.newerp.web.competitors;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.catalog.ProductSortService;
import ua.newerp.application.catalog.SortProductDto;
import ua.newerp.application.competitors.AddCompetitorLinkRequest;
import ua.newerp.application.competitors.CompetitorCheckResult;
import ua.newerp.application.competitors.CompetitorDto;
import ua.newerp.application.competitors.CompetitorLinkDto;
import ua.newerp.application.competitors.CompetitorReportDto;
import ua.newerp.application.competitors.CompetitorService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Search;

/** Competitor price monitoring: our price against theirs, the watched product pages, and the competitors. */
@Route("competitors")
@PageTitle("Ціни конкурентів — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class CompetitorPricesView extends VerticalLayout {

    final TabSheet tabs = new TabSheet();
    final Grid<CompetitorReportDto.Row> report = new Grid<>();
    final Button checkAll = new Button("Перевірити всі ціни", VaadinIcon.REFRESH.create());

    final Select<CompetitorDto> competitor = new Select<>();
    final ComboBox<SortProductDto> product = new ComboBox<>("Товар");
    final TextField url = new TextField("Сторінка в конкурента");
    final Button addLink = new Button("Додати", VaadinIcon.PLUS.create());
    final Grid<CompetitorLinkDto> links = new Grid<>();

    final Grid<CompetitorDto> competitors = new Grid<>();
    final Button addCompetitor = new Button("Новий конкурент", VaadinIcon.PLUS.create());

    private final CompetitorService service;
    private final VerticalLayout reportTab = new VerticalLayout();
    CompetitorReportDto data;

    public CompetitorPricesView(CompetitorService service, ProductSortService products) {
        this.service = service;
        tabs.add("Порівняння", reportTab);
        tabs.add("Сторінки товарів", linksTab(products));
        tabs.add("Конкуренти", competitorsTab());
        tabs.setWidthFull();

        checkAll.addClickListener(e -> checkAll());
        var hint = new Paragraph("Ціни читаються зі сторінок щоранку о 7:00. Різниця — на скільки наша ціна вища (+) чи нижча (−) "
                + "за найнижчу ціну конкурентів.");
        hint.addClassName("secondary");
        reportTab.setPadding(false);
        reportTab.add(hint, checkAll, report);

        add(new H2("Ціни конкурентів"), tabs);
        load();
    }

    void load() {
        List<CompetitorDto> all = service.competitors();
        competitors.setItems(all);
        competitor.setItems(all.stream().filter(CompetitorDto::active).toList());
        links.setItems(service.links(null));
        data = service.report();
        buildReport();
    }

    void checkAll() {
        Actions.run(null, () -> {
            CompetitorCheckResult result = service.checkAll();
            load();
            String text = "Перевірено сторінок: " + result.checked() + ", ціна змінилась: " + result.changed();
            if (result.failed() > 0) {
                Messages.warning(text + ", не вдалося: " + result.failed() + " (причина — на вкладці «Сторінки товарів»).");
            } else {
                Messages.success(text + ".");
            }
        });
    }

    void addLink() {
        if (competitor.getValue() == null || product.getValue() == null || url.getValue().isBlank()) {
            Messages.warning("Оберіть конкурента й товар і вставте адресу сторінки.");
            return;
        }
        Actions.run("Сторінку додано.", () -> {
            CompetitorLinkDto link = service.addLink(
                    new AddCompetitorLinkRequest(competitor.getValue().id(), product.getValue().productId(), url.getValue().strip()));
            service.check(link.id());
            product.clear();
            url.clear();
            load();
        });
    }

    void check(CompetitorLinkDto link) {
        Actions.run(null, () -> {
            CompetitorLinkDto result = service.check(link.id());
            load();
            if (result.lastError().isEmpty()) {
                Messages.success("Ціна: " + Fmt.money(result.lastPrice()) + ".");
            } else {
                Messages.warning(result.lastError());
            }
        });
    }

    void remove(CompetitorLinkDto link) {
        Actions.run("Сторінку видалено.", () -> {
            service.deleteLink(link.id());
            load();
        });
    }

    private void buildReport() {
        report.removeAllColumns();
        report.addColumn(CompetitorReportDto.Row::productName).setHeader("Товар").setAutoWidth(true).setFlexGrow(1);
        report.addColumn(r -> Fmt.money(r.ourPrice())).setHeader("Наша ціна").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        for (int i = 0; i < data.competitors().size(); i++) {
            int index = i;
            report.addComponentColumn(r -> price(r, r.prices().get(index))).setHeader(data.competitors().get(i).name())
                    .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        }
        report.addComponentColumn(CompetitorPricesView::difference).setHeader("Різниця").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        report.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        report.setEmptyStateText("Додайте сторінки товарів у конкурентів на вкладці «Сторінки товарів».");
        report.setAllRowsVisible(true);
        report.setItems(data.rows());
    }

    private static Component price(CompetitorReportDto.Row row, BigDecimal value) {
        var text = new Span(value == null ? "—" : Fmt.money(value));
        if (value != null && row.lowest() != null && value.compareTo(row.lowest()) == 0) {
            text.getStyle().set("font-weight", "600");
        }
        return text;
    }

    static Component difference(CompetitorReportDto.Row row) {
        BigDecimal d = row.differencePercent();
        if (d == null) {
            return new Span("");
        }
        var badge = new Span((d.signum() > 0 ? "+" : "") + Fmt.qty(d) + "%");
        badge.addClassName("status-badge");
        badge.addClassName(d.signum() > 0 ? "tone-2" : "tone-1");
        return badge;
    }

    private Component linksTab(ProductSortService products) {
        competitor.setLabel("Конкурент");
        competitor.setItemLabelGenerator(CompetitorDto::name);
        Search.bind(product, 2, products::searchProducts);
        product.setItemLabelGenerator(p -> p.name() + " (№" + p.productId() + ")");
        product.setPlaceholder("Назва");
        product.setMinWidth("18rem");
        url.setMaxLength(500);
        url.setPlaceholder("https://");
        url.setMinWidth("22rem");
        addLink.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addLink.addClickListener(e -> addLink());
        var form = new HorizontalLayout(competitor, product, url, addLink);
        form.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        form.setWrap(true);

        links.addColumn(CompetitorLinkDto::productName).setHeader("Товар").setAutoWidth(true).setFlexGrow(1);
        links.addColumn(CompetitorLinkDto::competitorName).setHeader("Конкурент").setAutoWidth(true);
        links.addComponentColumn(l -> {
            var page = new Anchor(l.url(), "сторінка");
            page.setTarget("_blank");
            return page;
        }).setAutoWidth(true);
        links.addColumn(l -> l.lastPrice() == null ? "—" : Fmt.money(l.lastPrice())).setHeader("Ціна")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        links.addColumn(l -> l.lastCheckedAt() == null ? "" : Fmt.date(l.lastCheckedAt())).setHeader("Перевірено").setAutoWidth(true);
        links.addComponentColumn(l -> {
            var error = new Span(l.lastError());
            error.getStyle().set("color", "var(--lumo-error-text-color)");
            return error;
        }).setHeader("Помилка").setAutoWidth(true);
        links.addComponentColumn(l -> {
            var check = new Button(VaadinIcon.REFRESH.create(), e -> check(l));
            check.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            check.setAriaLabel("Перевірити");
            var edit = new Button(VaadinIcon.EDIT.create(), e -> new LinkDialog(service, l, this::load).open());
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            edit.setAriaLabel("Ціна вручну та історія");
            var remove = new Button(VaadinIcon.TRASH.create(), e -> Confirm.ask("Видалити сторінку?",
                    "Сторінка «" + l.productName() + "» у «" + l.competitorName() + "» зникне разом з історією цін.", "Видалити", "Ні",
                    () -> remove(l)));
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            remove.setAriaLabel("Видалити");
            return new HorizontalLayout(check, edit, remove);
        }).setAutoWidth(true);
        links.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        links.setEmptyStateText("Сторінок ще немає.");
        links.setAllRowsVisible(true);

        var layout = new VerticalLayout(form, links);
        layout.setPadding(false);
        return layout;
    }

    private Component competitorsTab() {
        addCompetitor.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addCompetitor.addClickListener(e -> new CompetitorDialog(service, null, this::load).open());
        competitors.addColumn(CompetitorDto::name).setHeader("Назва").setAutoWidth(true);
        competitors.addColumn(CompetitorDto::siteUrl).setHeader("Сайт").setAutoWidth(true).setFlexGrow(1);
        competitors.addColumn(CompetitorDto::links).setHeader("Сторінок").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        competitors.addColumn(c -> c.active() ? "" : "не стежимо").setHeader("Стан").setAutoWidth(true);
        competitors.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        competitors.setEmptyStateText("Конкурентів ще немає.");
        competitors.addItemClickListener(e -> new CompetitorDialog(service, e.getItem(), this::load).open());
        competitors.setAllRowsVisible(true);
        var layout = new VerticalLayout(addCompetitor, competitors);
        layout.setPadding(false);
        return layout;
    }
}
