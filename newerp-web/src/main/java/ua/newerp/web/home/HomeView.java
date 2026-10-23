package ua.newerp.web.home;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.PermitAll;
import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.reports.ReportService;
import ua.newerp.application.stock.StockAlertService;
import ua.newerp.web.Access;
import ua.newerp.web.admin.UsersView;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.finance.BankPaymentsView;
import ua.newerp.web.finance.CashboxView;
import ua.newerp.web.orders.OrderListView;
import ua.newerp.web.production.ModelListView;
import ua.newerp.web.stock.StockListView;

/** The home page: a greeting and a card for each section the user's role opens (Home.razor). */
@Route("")
@PageTitle("NewErp")
@PermitAll
public class HomeView extends VerticalLayout {

    public HomeView(CurrentSession session, ReportService reports, StockAlertService alerts) {
        String name = session.user().map(CurrentUser::fullName).filter(n -> !n.isBlank())
                .or(() -> session.user().map(CurrentUser::username)).orElse("");
        add(new H2("Вітаємо, " + name + "!"));
        if (session.hasAnyRole(Access.ORDERS)) {
            try {
                add(new DashboardPanel(reports.dashboard()));
            } catch (RuntimeException e) {
                // The summary is a bonus: the page still works without it.
                var warning = new Paragraph("Зведення продажів зараз недоступне: " + e.getMessage());
                warning.addClassNames("note", "tone-2");
                add(warning);
            }
        }

        if (session.hasAnyRole(Access.STOCK)) {
            try {
                add(new StockAlertsPanel(alerts.get()));
            } catch (RuntimeException e) {
                var warning = new Paragraph("Залишки зараз недоступні: " + e.getMessage());
                warning.addClassNames("note", "tone-2");
                add(warning);
            }
        }

        var cards = new Div();
        cards.addClassName("section-cards");
        if (session.hasAnyRole(Access.STOCK)) {
            cards.add(card(StockListView.class, VaadinIcon.STORAGE, "Склад",
                    "Розхідники, прихід і витрата, що дозамовити, замовлення постачальникам."));
            cards.add(card(ModelListView.class, VaadinIcon.SCISSORS, "Виробництво", "Моделі й розміри, крій і пошив, що треба шити."));
        }
        if (session.hasAnyRole(Access.ORDERS)) {
            cards.add(card(OrderListView.class, VaadinIcon.CART, "Замовлення", "Список, статуси, менеджери, SMS клієнтам."));
        }
        if (session.hasAnyRole(Access.FINANCE)) {
            cards.add(card(CashboxView.class, VaadinIcon.CASH, "Каса", "Зміни касирів Checkbox, фіскальні чеки до замовлень, X- і Z-звіти."));
            cards.add(card(BankPaymentsView.class, VaadinIcon.PIGGY_BANK, "Надходження на рахунок",
                    "Виписка ПриватБанку й прив'язка платежів до замовлень."));
        }
        if (session.hasAnyRole(Access.ADMIN)) {
            cards.add(card(UsersView.class, VaadinIcon.USERS, "Користувачі", "Доступ до NewErp, ролі, тимчасові паролі."));
        }
        add(cards);
    }

    private static Component card(Class<? extends Component> view, VaadinIcon icon, String title, String text) {
        var link = new RouterLink(view);
        link.addClassName("section-card");
        var heading = new H3(icon.create(), new Span(title));
        heading.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.SMALL);
        link.add(heading, new Paragraph(text));
        return link;
    }
}
