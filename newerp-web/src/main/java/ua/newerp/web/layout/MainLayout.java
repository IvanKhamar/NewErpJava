package ua.newerp.web.layout;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.web.Access;
import ua.newerp.web.auth.ChangePasswordView;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.customers.CustomerListView;
import ua.newerp.web.delivery.DeliveryView;
import ua.newerp.web.home.HomeView;
import ua.newerp.web.finance.BankPaymentsView;
import ua.newerp.web.finance.CashboxView;
import ua.newerp.web.locations.LocationsView;
import ua.newerp.web.partners.PartnerListView;
import ua.newerp.web.orders.OrderListView;
import ua.newerp.web.reports.ReportsView;
import ua.newerp.web.returns.ReturnListView;
import ua.newerp.web.production.ModelListView;
import ua.newerp.web.production.ProductionLogView;
import ua.newerp.web.production.SewingPlanView;
import ua.newerp.web.stock.PurchaseOrderListView;
import ua.newerp.web.stock.ReorderView;
import ua.newerp.web.stock.StockListView;
import ua.newerp.web.stock.SuppliersView;

/**
 * The frame around every page: top bar with the user, side menu (MainLayout.razor + NavMenu.razor).
 * Each menu item is shown only to the roles its view allows. @Layout puts every view into this frame automatically.
 */
@Layout
@AnonymousAllowed
public class MainLayout extends AppLayout implements BeforeEnterObserver {

    private final CurrentSession session;

    public MainLayout(CurrentSession session) {
        this.session = session;
        setPrimarySection(Section.NAVBAR);

        var title = new Span("NewErp");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.SEMIBOLD);
        var bar = new HorizontalLayout(new DrawerToggle(), title);
        bar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        bar.setWidthFull();
        bar.addClassNames(LumoUtility.Padding.End.MEDIUM);

        session.user().ifPresent(user -> {
            var name = new Span(user.fullName().isBlank() ? user.username() : user.fullName());
            name.addClassNames(LumoUtility.Margin.Start.AUTO, LumoUtility.FontSize.SMALL);
            var password = new Button(VaadinIcon.KEY.create(), e -> getUI().ifPresent(ui -> ui.navigate(ChangePasswordView.class)));
            password.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            password.setTooltipText("Змінити пароль");
            password.setAriaLabel("Змінити пароль");
            var logout = new Button(VaadinIcon.SIGN_OUT.create(), e -> session.logout());
            logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            logout.setTooltipText("Вийти");
            logout.setAriaLabel("Вийти");
            bar.add(name, password, logout);
        });
        addToNavbar(bar);
        addToDrawer(menu());
    }

    private SideNav menu() {
        var nav = new SideNav();
        nav.addItem(new SideNavItem("Головна", HomeView.class, VaadinIcon.HOME.create()));
        if (session.hasAnyRole(Access.STOCK)) {
            nav.addItem(new SideNavItem("Розхідники", StockListView.class, VaadinIcon.STORAGE.create()));
            nav.addItem(new SideNavItem("Що дозамовити", ReorderView.class, VaadinIcon.LIST.create()));
            nav.addItem(new SideNavItem("Закупівлі", PurchaseOrderListView.class, VaadinIcon.PACKAGE.create()));
            nav.addItem(new SideNavItem("Постачальники", SuppliersView.class, VaadinIcon.FACTORY.create()));
            nav.addItem(new SideNavItem("Моделі", ModelListView.class, VaadinIcon.SCISSORS.create()));
            nav.addItem(new SideNavItem("План пошиву", SewingPlanView.class, VaadinIcon.CALENDAR_CLOCK.create()));
            nav.addItem(new SideNavItem("Журнал пошиву", ProductionLogView.class, VaadinIcon.CLIPBOARD_TEXT.create()));
        }
        if (session.hasAnyRole(Access.ORDERS)) {
            nav.addItem(new SideNavItem("Замовлення", OrderListView.class, VaadinIcon.CART.create()));
            nav.addItem(new SideNavItem("Повернення", ReturnListView.class, VaadinIcon.ARROW_BACKWARD.create()));
            nav.addItem(new SideNavItem("Покупці", CustomerListView.class, VaadinIcon.USERS.create()));
            nav.addItem(new SideNavItem("Доставка", DeliveryView.class, VaadinIcon.TRUCK.create()));
            nav.addItem(new SideNavItem("Звіти", ReportsView.class, VaadinIcon.CHART.create()));
            nav.addItem(new SideNavItem("Склади й пункти видачі", LocationsView.class, VaadinIcon.SHOP.create()));
            nav.addItem(new SideNavItem("Партнери", PartnerListView.class, VaadinIcon.CONNECT.create()));
        }
        if (session.hasAnyRole(Access.FINANCE)) {
            nav.addItem(new SideNavItem("Каса", CashboxView.class, VaadinIcon.CASH.create()));
            nav.addItem(new SideNavItem("Надходження на рахунок", BankPaymentsView.class, VaadinIcon.PIGGY_BANK.create()));
        }
        return nav;
    }

    // A temporary password from the admin: until it is changed, only the change page opens.
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        boolean mustChange = session.user().map(CurrentUser::mustChangePassword).orElse(false);
        if (mustChange && event.getNavigationTarget() != ChangePasswordView.class) {
            event.forwardTo(ChangePasswordView.class);
        }
    }
}
