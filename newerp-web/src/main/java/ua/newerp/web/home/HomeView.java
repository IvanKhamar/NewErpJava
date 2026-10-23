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
import ua.newerp.web.Access;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.orders.OrderListView;

/** The home page: a greeting and a card for each section the user's role opens (Home.razor). */
@Route("")
@PageTitle("NewErp")
@PermitAll
public class HomeView extends VerticalLayout {

    public HomeView(CurrentSession session) {
        String name = session.user().map(CurrentUser::fullName).filter(n -> !n.isBlank())
                .or(() -> session.user().map(CurrentUser::username)).orElse("");
        add(new H2("Вітаємо, " + name + "!"));

        var cards = new Div();
        cards.addClassName("section-cards");
        if (session.hasAnyRole(Access.ORDERS)) {
            cards.add(card(OrderListView.class, VaadinIcon.CART, "Замовлення", "Список, статуси, менеджери, SMS клієнтам."));
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
