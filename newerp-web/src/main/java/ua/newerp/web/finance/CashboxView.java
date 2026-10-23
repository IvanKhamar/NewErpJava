package ua.newerp.web.finance;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
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
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.cashbox.CashboxService;
import ua.newerp.application.cashbox.CashboxSetupDto;
import ua.newerp.application.cashbox.CashierDto;
import ua.newerp.application.cashbox.ChequeDto;
import ua.newerp.application.cashbox.ChequeFilter;
import ua.newerp.application.cashbox.DirectoryEntryDto;
import ua.newerp.application.cashbox.SaveDirectoryEntryRequest;
import ua.newerp.application.cashbox.WorkShiftDto;
import ua.newerp.application.cashbox.WorkShiftFilter;
import ua.newerp.application.cashbox.ZReportDto;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Paging;

/**
 * The Checkbox cash register (Cashbox.razor): cashiers with their shifts, receipts, shifts with Z reports, and for the admin
 * the warehouses and requisites.
 */
@Route("cashbox")
@PageTitle("Каса — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class CashboxView extends VerticalLayout {

    /** The receipt filter: all, not accepted by Checkbox, fiscalized. */
    enum ChequeState { ALL, NOT_ACCEPTED, FISCALIZED }

    final Grid<CashierDto> cashiers = new Grid<>();
    final Select<ChequeState> chequeState = new Select<>();
    final Grid<ChequeDto> cheques = new Grid<>();
    final Grid<WorkShiftDto> shifts = new Grid<>();
    final Grid<ZReportDto> reports = new Grid<>();
    final Button newCashier = new Button("Новий касир", VaadinIcon.PLUS.create());
    final Div directories = new Div();
    private final Paragraph fakeMode = new Paragraph("Ключі Checkbox не задано: працює тестова заглушка, чеки не фіскальні. "
            + "Так і має бути в локальній розробці.");
    private final CashboxService service;
    private final CurrentSession session;
    private final boolean admin;
    CashboxSetupDto setup;

    public CashboxView(CashboxService service, CurrentSession session) {
        this.service = service;
        this.session = session;
        admin = session.hasAnyRole(Access.ADMIN);
        fakeMode.addClassNames("note", "tone-0");

        newCashier.addClickListener(e -> new CashierDialog(service, setup, null, this::load).open());
        newCashier.setVisible(admin);
        cashiersGrid();
        chequesGrid();
        shiftsGrids();

        var tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("Касири", new Div(newCashier, cashiers));
        chequeState.setLabel("Які чеки");
        chequeState.setItems(ChequeState.values());
        chequeState.setItemLabelGenerator(s -> switch (s) {
            case ALL -> "усі";
            case NOT_ACCEPTED -> "не прийняті Checkbox";
            case FISCALIZED -> "фіскалізовані";
        });
        chequeState.setValue(ChequeState.ALL);
        chequeState.addValueChangeListener(e -> cheques.getDataProvider().refreshAll());
        tabs.add("Чеки", new Div(chequeState, cheques));
        tabs.add("Зміни й Z-звіти", new Div(new H3("Зміни"), shifts, new H3("Z-звіти"), reports));
        if (admin) {
            tabs.add("Склади й реквізити", directories);
        }
        add(new H2("Каса"), fakeMode, tabs);
        load();
    }

    void load() {
        Actions.run(null, () -> {
            setup = service.setup();
            fakeMode.setVisible(setup.fakeMode());
            cashiers.setItems(setup.cashiers());
            cheques.getDataProvider().refreshAll();
            shifts.setItems(service.shifts(new WorkShiftFilter(null, null, null)));
            reports.setItems(service.reports(null, null, null));
            if (admin) {
                directories.removeAll();
                var columns = new Div(directory("Склади (точки продажу)", setup.warehouses(), service::createWarehouse,
                                service::updateWarehouse, service::deleteWarehouse),
                        directory("Реквізити (ФОП)", setup.requisites(), service::createRequisites, service::updateRequisites,
                                service::deleteRequisites));
                columns.addClassName("two-columns");
                directories.add(columns);
            }
        });
    }

    ChequeFilter chequeFilter(int page, int pageSize) {
        Boolean fiscalized = switch (chequeState.getValue()) {
            case ALL -> null;
            case NOT_ACCEPTED -> false;
            case FISCALIZED -> true;
        };
        return new ChequeFilter(null, null, null, null, fiscalized, page, pageSize);
    }

    void openShift(CashierDto cashier) {
        int userId = session.user().orElseThrow().userId();
        run("Зміну касира «" + cashier.name() + "» відкрито.", () -> service.openShift(cashier.id(), userId));
    }

    void closeShift(CashierDto cashier) {
        int userId = session.user().orElseThrow().userId();
        Confirm.ask("Закрити зміну?", "Буде створено Z-звіт, і касир «" + cashier.name() + "» не зможе видавати чеки до нової зміни.",
                "Закрити зміну", "Ні", () -> run(null, () -> {
                    Optional<ZReportDto> report = service.closeShift(cashier.id(), userId);
                    if (report.isPresent()) {
                        Messages.success("Зміну закрито, Z-звіт створено.");
                    } else {
                        Messages.warning("Checkbox ще закриває зміну. Звірте її трохи згодом.");
                    }
                }));
    }

    private void cashiersGrid() {
        cashiers.addComponentColumn(c -> {
            var cell = new Span(c.name());
            if (!c.isActive()) {
                cell.add(badge("вимкнений", "secondary"));
            }
            if (!c.canFiscalize()) {
                cell.add(badge("немає ключів Checkbox", "tone-2"));
            }
            return cell;
        }).setHeader("Касир").setFlexGrow(1);
        cashiers.addColumn(c -> c.paymentCode() == null || c.paymentCode().isEmpty() ? "за замовчуванням" : c.paymentCode())
                .setHeader("Для оплати").setAutoWidth(true);
        cashiers.addColumn(c -> orDash(c.warehouse()) + " / " + orDash(c.requisites())).setHeader("Склад / реквізити").setAutoWidth(true);
        cashiers.addComponentColumn(c -> {
            var state = new Div();
            if (c.openShiftStart() != null) {
                state.add(badge("відкрита з " + Fmt.date(c.openShiftStart()), "tone-1"));
            } else {
                state.add(secondary("закрита"));
            }
            if (c.autoCloseTime() != null) {
                state.add(new Div(secondary("автозакриття о " + c.autoCloseTime())));
            }
            return state;
        }).setHeader("Зміна").setFlexGrow(1);
        cashiers.addComponentColumn(this::cashierActions).setAutoWidth(true);
        cashiers.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        cashiers.setEmptyStateText("Касирів ще немає.");
        cashiers.setAllRowsVisible(true);
    }

    private Component cashierActions(CashierDto cashier) {
        var actions = new HorizontalLayout();
        actions.setSpacing(false);
        actions.addClassName(LumoUtility.Gap.XSMALL);
        actions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        if (cashier.openShiftId() == null) {
            var open = new Button("Відкрити зміну", e -> openShift(cashier));
            open.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            open.setEnabled(cashier.canFiscalize() && cashier.isActive());
            actions.add(open);
        } else {
            var x = new Button("X-звіт", e -> Actions.run(null, () -> showReport("X-звіт: " + cashier.name(),
                    service.xReport(cashier.id()).text())));
            x.addThemeVariants(ButtonVariant.LUMO_SMALL);
            var close = new Button("Закрити зміну (Z)", e -> closeShift(cashier));
            close.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            actions.add(x, close);
        }
        if (admin) {
            var edit = new Button(VaadinIcon.EDIT.create(), e -> new CashierDialog(service, setup, cashier, this::load).open());
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            edit.setAriaLabel("Змінити касира");
            actions.add(edit);
        }
        return actions;
    }

    private void chequesGrid() {
        cheques.addComponentColumn(c -> new Div(new Div("№" + c.id()), secondary(Fmt.date(c.dateAdded())))).setHeader("Чек")
                .setAutoWidth(true);
        cheques.addComponentColumn(c -> new Span(new Anchor("orders/" + c.orderId(), "№" + c.orderId()), new Span(" " + c.customer())))
                .setHeader("Замовлення").setFlexGrow(1);
        cheques.addColumn(ChequeDto::cashier).setHeader("Касир").setAutoWidth(true);
        cheques.addColumn(c -> Fmt.money(c.sum())).setHeader("Сума").setAutoWidth(true);
        cheques.addComponentColumn(c -> {
            if (!c.isFiscalized()) {
                return badge("не прийнято", "tone-3");
            }
            var state = new Div(badge("фіскалізовано", "tone-1"));
            if (c.reportId() != 0) {
                state.add(new Div(secondary("зміну закрито")));
            }
            return state;
        }).setHeader("Стан").setAutoWidth(true);
        cheques.addComponentColumn(c -> {
            var actions = new HorizontalLayout();
            actions.setSpacing(false);
            actions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            if (c.receiptUrl() != null) {
                var receipt = new Anchor(c.receiptUrl(), "Чек");
                receipt.setTarget(AnchorTarget.BLANK);
                actions.add(receipt);
            }
            if (!c.isFiscalized()) {
                var retry = new Button("Повторити", e -> run("Чек фіскалізовано.", () -> service.fiscalize(c.id())));
                retry.addThemeVariants(ButtonVariant.LUMO_SMALL);
                var delete = new Button(VaadinIcon.TRASH.create(), e -> run("Чек видалено.", () -> service.deleteCheque(c.id())));
                delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                delete.setAriaLabel("Видалити чек");
                actions.add(retry, delete);
            }
            return actions;
        }).setAutoWidth(true);
        cheques.addThemeVariants(GridVariant.LUMO_COMPACT);
        cheques.setEmptyStateText("Чеків немає.");
        cheques.setHeight("32rem");
        cheques.setItems(q -> Paging.rows(q.getOffset(), q.getLimit(), ChequeFilter.DEFAULT_PAGE_SIZE,
                        page -> service.cheques(chequeFilter(page, ChequeFilter.DEFAULT_PAGE_SIZE)).items()).stream(),
                q -> service.cheques(chequeFilter(1, 1)).totalCount());
    }

    private void shiftsGrids() {
        shifts.addColumn(WorkShiftDto::cashier).setHeader("Касир").setAutoWidth(true);
        shifts.addColumn(s -> Fmt.date(s.dateStart()) + " " + s.openedBy()).setHeader("Відкрито").setAutoWidth(true);
        shifts.addColumn(s -> s.dateEnd() == null ? "" : Fmt.date(s.dateEnd())).setHeader("Закрито").setAutoWidth(true);
        shifts.addColumn(WorkShiftDto::cheques).setHeader("Чеків").setAutoWidth(true);
        shifts.addColumn(s -> Fmt.money(s.chequesSum())).setHeader("Сума").setAutoWidth(true);
        shifts.addComponentColumn(s -> {
            if (s.dateEnd() != null) {
                return new Span();
            }
            var sync = new Button("Звірити з Checkbox", e -> {
                int userId = session.user().orElseThrow().userId();
                run(null, () -> Messages.success(service.syncShift(s.id(), userId).isPresent()
                        ? "Каса вже закрила зміну: Z-звіт записано." : "У Checkbox зміна ще відкрита."));
            });
            sync.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return sync;
        }).setAutoWidth(true);
        shifts.addThemeVariants(GridVariant.LUMO_COMPACT);
        shifts.setEmptyStateText("Змін ще не було.");
        shifts.setAllRowsVisible(true);

        reports.addColumn(r -> Fmt.date(r.dateAdded())).setHeader("Дата").setAutoWidth(true);
        reports.addColumn(ZReportDto::cashier).setHeader("Касир").setAutoWidth(true);
        reports.addColumn(r -> r.closedBy() == null ? "автоматично" : r.closedBy()).setHeader("Хто закрив").setFlexGrow(1);
        reports.addComponentColumn(r -> {
            var text = new Button("Текст звіту", e -> Actions.run(null, () -> showReport(
                    "Z-звіт: " + r.cashier() + ", " + Fmt.date(r.dateAdded()), service.zReportText(r.id()).text())));
            text.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return text;
        }).setAutoWidth(true);
        reports.addThemeVariants(GridVariant.LUMO_COMPACT);
        reports.setEmptyStateText("Z-звітів ще немає.");
        reports.setAllRowsVisible(true);
    }

    /** A list of warehouses or requisites with add, rename and delete. */
    private Component directory(String title, List<DirectoryEntryDto> entries, ToIntFunction<SaveDirectoryEntryRequest> create,
            EntryUpdate update, IntConsumer delete) {
        var heading = new H3(title);
        heading.addClassName(LumoUtility.Margin.End.AUTO);
        var add = new Button(VaadinIcon.PLUS.create(), e -> prompt("Додати", "", name -> create.applyAsInt(
                new SaveDirectoryEntryRequest(name, null))));
        add.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        add.setAriaLabel("Додати");
        var header = new HorizontalLayout(heading, add);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        var list = new Div(header);
        for (DirectoryEntryDto entry : entries) {
            var name = new Span(entry.name());
            name.addClassName(LumoUtility.Margin.End.AUTO);
            var rename = new Button(VaadinIcon.EDIT.create(), e -> prompt("Перейменувати", entry.name(),
                    value -> update.apply(entry.id(), new SaveDirectoryEntryRequest(value, entry.description()))));
            rename.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            rename.setAriaLabel("Перейменувати");
            var remove = new Button(VaadinIcon.TRASH.create(), e -> run("«" + entry.name() + "» видалено.", () -> delete.accept(entry.id())));
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            remove.setAriaLabel("Видалити");
            var row = new HorizontalLayout(name, rename, remove);
            row.setWidthFull();
            row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            list.add(row);
        }
        list.addClassName("panel");
        return list;
    }

    /** updateWarehouse and updateRequisites: (id, request). */
    @FunctionalInterface
    interface EntryUpdate {
        void apply(int id, SaveDirectoryEntryRequest request);
    }

    private void prompt(String title, String value, Consumer<String> onSave) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(title);
        var name = new TextField("Назва", value, "");
        name.setWidthFull();
        dialog.add(name);
        var save = new Button("Зберегти", e -> run("Збережено.", () -> {
            onSave.accept(name.getValue());
            dialog.close();
        }));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Скасувати", e -> dialog.close()), save);
        dialog.open();
    }

    private void showReport(String title, String text) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setWidth("min(36rem, 100%)");
        var pre = new Pre(text);
        pre.getStyle().set("white-space", "pre-wrap").set("font-size", "0.85rem");
        dialog.add(pre);
        dialog.getFooter().add(new Button("Закрити", e -> dialog.close()));
        dialog.open();
    }

    /** Runs an action and reloads the page; success: the message, or null when the action shows its own. */
    private void run(String success, Runnable action) {
        if (Actions.run(success, action)) {
            load();
        }
    }

    private static Span badge(String text, String tone) {
        var badge = new Span(text);
        badge.addClassNames("status-badge", tone);
        badge.getStyle().set("margin-left", "var(--lumo-space-xs)");
        return badge;
    }

    private static Span secondary(String text) {
        var span = new Span(text);
        span.addClassNames("secondary", LumoUtility.FontSize.SMALL);
        return span;
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }
}
