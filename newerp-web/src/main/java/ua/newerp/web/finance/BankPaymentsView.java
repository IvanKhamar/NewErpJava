package ua.newerp.web.finance;

import java.util.List;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.payments.BankImportResult;
import ua.newerp.application.payments.BankPaymentDto;
import ua.newerp.application.payments.BankPaymentFilter;
import ua.newerp.application.payments.BankPaymentService;
import ua.newerp.application.payments.LinkPaymentRequest;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Paging;

/** Incoming bank payments (BankPayments.razor): load the PrivatBank statement and match payments with orders. */
@Route("payments")
@PageTitle("Надходження на рахунок — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class BankPaymentsView extends VerticalLayout {

    static final String UNLINKED = "unlinked";
    static final String LINKED = "linked";
    static final String ALL = "all";
    static final String HIDDEN = "hidden";

    final Button importStatement = new Button("Завантажити виписку", VaadinIcon.DOWNLOAD.create());
    final Select<String> view = new Select<>();
    final TextField search = new TextField("Пошук у призначенні, платнику чи сумі");
    final Grid<BankPaymentDto> grid = new Grid<>();
    private final Div importResult = new Div();
    private final BankPaymentService service;

    public BankPaymentsView(BankPaymentService service) {
        this.service = service;
        setSizeFull();
        importStatement.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        importStatement.addClickListener(e -> importStatement());
        var title = new H2("Надходження на рахунок");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, importStatement);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        view.setLabel("Показати");
        view.setItems(UNLINKED, LINKED, ALL, HIDDEN);
        view.setItemLabelGenerator(v -> switch (v) {
            case UNLINKED -> "не прив'язані";
            case LINKED -> "прив'язані";
            case HIDDEN -> "приховані";
            default -> "усі";
        });
        view.setValue(UNLINKED);
        view.addValueChangeListener(e -> refresh());
        search.setMinWidth("22rem");
        search.setClearButtonVisible(true);
        search.addKeyPressListener(Key.ENTER, e -> refresh());
        search.addValueChangeListener(e -> {
            if (e.getValue().isEmpty()) {
                refresh(); // the clear button
            }
        });
        var filters = new HorizontalLayout(view, search);
        filters.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        grid.addColumn(p -> Fmt.date(p.timestamp())).setHeader("Дата").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            var amount = new Span(Fmt.money(p.amount()));
            amount.addClassName(LumoUtility.FontWeight.BOLD);
            return amount;
        }).setHeader("Сума").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            var description = new Div(p.description());
            description.addClassNames("secondary", "pre-line", LumoUtility.FontSize.SMALL);
            return new Div(new Div(p.payer()), description);
        }).setHeader("Платник і призначення").setFlexGrow(1);
        grid.addComponentColumn(p -> p.orderId() == 0 ? new Span()
                : new Span(new Anchor("orders/" + p.orderId(), "№" + p.orderId()), new Span(" " + p.orderCustomer())))
                .setHeader("Замовлення").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setEmptyStateText("Надходжень немає.");
        grid.setSizeFull();
        grid.setItems(q -> fetch(q.getOffset(), q.getLimit()).stream(), q -> service.list(filter(1, 1)).totalCount());

        add(header, importResult, filters, grid);
        setFlexGrow(1, grid);
    }

    List<BankPaymentDto> fetch(int offset, int limit) {
        return Paging.rows(offset, limit, BankPaymentFilter.DEFAULT_PAGE_SIZE,
                page -> service.list(filter(page, BankPaymentFilter.DEFAULT_PAGE_SIZE)).items());
    }

    BankPaymentFilter filter(int page, int pageSize) {
        Boolean linked = switch (view.getValue()) {
            case LINKED -> true;
            case UNLINKED -> false;
            default -> null;
        };
        return new BankPaymentFilter(null, null, linked, null, search.getValue(), HIDDEN.equals(view.getValue()), page, pageSize);
    }

    void importStatement() {
        Actions.run(null, () -> {
            BankImportResult result = service.importStatement(null, null);
            importResult.removeAll();
            var note = new Paragraph(result.clientConfigured()
                    ? "Отримано операцій: " + result.fetched() + ", нових надходжень: " + result.added() + "."
                    : "Ключі ПриватБанку не задано, тож виписка не завантажувалась. Так і має бути в локальній розробці.");
            note.addClassNames("note", result.clientConfigured() ? "tone-1" : "tone-0");
            importResult.add(note);
            refresh();
        });
    }

    private HorizontalLayout actions(BankPaymentDto payment) {
        var actions = new HorizontalLayout();
        actions.setSpacing(false);
        actions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        if (payment.orderId() == 0) {
            var match = new Button("Підібрати замовлення", e -> new MatchPaymentDialog(service, payment, linked -> {
                Messages.success("Платіж прив'язано до замовлення №" + linked.orderId() + ".");
                refresh();
            }).open());
            match.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            actions.add(match);
        } else {
            var unlink = new Button("Відв'язати", e -> Actions.run("Платіж відв'язано.", () -> {
                service.link(payment.id(), new LinkPaymentRequest(0));
                refresh();
            }));
            unlink.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            actions.add(unlink);
        }
        boolean visible = payment.isVisible();
        var toggle = new Button((visible ? VaadinIcon.EYE_SLASH : VaadinIcon.EYE).create(),
                e -> Actions.run(visible ? "Платіж приховано." : "Платіж повернуто до списку.", () -> {
                    service.setVisibility(payment.id(), !visible);
                    refresh();
                }));
        toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        toggle.setAriaLabel(visible ? "Приховати (не стосується замовлень)" : "Повернути до списку");
        toggle.setTooltipText(toggle.getAriaLabel().orElse(""));
        actions.add(toggle);
        return actions;
    }

    private void refresh() {
        grid.getDataProvider().refreshAll();
    }
}
