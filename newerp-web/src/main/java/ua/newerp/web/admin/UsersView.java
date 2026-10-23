package ua.newerp.web.admin;

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.auth.ResetPasswordRequest;
import ua.newerp.application.auth.SetAccessRequest;
import ua.newerp.application.auth.UserAdminService;
import ua.newerp.application.auth.UserDto;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Confirm;

/** NewErp access for the old site's users (Users.razor): give access, change the role, reset the password, take access away. */
@Route("users")
@PageTitle("Користувачі — NewErp")
@RolesAllowed(Access.ADMIN)
public class UsersView extends VerticalLayout {

    final Button create = new Button("Новий працівник", VaadinIcon.PLUS.create());
    final Checkbox withAccessOnly = new Checkbox("Лише ті, хто має доступ до NewErp", true);
    final Grid<UserDto> grid = new Grid<>();

    private final UserAdminService service;
    private final int selfId;
    private List<UserDto> users = List.of();

    public UsersView(UserAdminService service, CurrentSession session) {
        this.service = service;
        this.selfId = session.user().orElseThrow().userId();
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(e -> new NewUserDialog(service, result -> {
            load();
            String username = users.stream().filter(u -> u.userId() == result.userId()).map(UserDto::username).findFirst().orElse("");
            showPassword(username, result.temporaryPassword());
        }).open());
        var title = new H2("Користувачі");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, create);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        var hint = new Paragraph("Усі користувачі старого сайту. Доступ до NewErp видається окремо; зміна ролі чи відкликання доступу діють "
                + "одразу, навіть якщо людина зараз у системі.");
        hint.addClassName("secondary");
        withAccessOnly.addValueChangeListener(e -> show());

        grid.addComponentColumn(u -> {
            var name = new Span(u.fullName());
            if (!u.activeOnOldSite()) {
                var off = new Span("вимкнено на старому сайті");
                off.addClassNames("status-badge", "tone-3");
                off.getStyle().set("margin-left", "var(--lumo-space-s)");
                return new Span(name, off);
            }
            return name;
        }).setHeader("Ім'я").setFlexGrow(1);
        grid.addColumn(UserDto::username).setHeader("Логін").setAutoWidth(true);
        grid.addColumn(UserDto::email).setHeader("Email").setAutoWidth(true);
        grid.addComponentColumn(u -> {
            var role = new Span(u.hasAccess() ? AdminUi.roleLabel(u.role()) : "немає доступу");
            role.addClassName(u.hasAccess() ? "status-badge" : "secondary");
            return role;
        }).setHeader("Роль у NewErp").setAutoWidth(true);
        grid.addColumn(u -> u.lastLoginAt() == null ? "" : Fmt.date(u.lastLoginAt())).setHeader("Останній вхід").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);

        add(header, hint, withAccessOnly, grid);
        load();
    }

    void setRole(UserDto user, String role) {
        Actions.run(user.hasAccess() ? "Роль змінено." : "Доступ видано.", () -> {
            var result = service.setAccess(selfId, user.userId(), new SetAccessRequest(role, null));
            load();
            showPassword(user.username(), result.temporaryPassword());
        });
    }

    void resetPassword(UserDto user) {
        Actions.run(null, () -> {
            var result = service.resetPassword(user.userId(), new ResetPasswordRequest(null));
            load();
            showPassword(user.username(), result.temporaryPassword());
        });
    }

    void revoke(UserDto user) {
        Actions.run("Доступ забрано.", () -> {
            service.revoke(selfId, user.userId());
            load();
        });
    }

    private HorizontalLayout actions(UserDto user) {
        var actions = new HorizontalLayout();
        actions.setSpacing(false);
        actions.add(small(user.hasAccess() ? "Змінити роль" : "Видати доступ", () -> new RoleDialog(
                user.hasAccess() ? "Роль: " + user.fullName() : "Видати доступ: " + user.fullName(), user.hasAccess() ? user.role() : null,
                user.userId() == selfId, role -> setRole(user, role)).open()));
        if (user.hasAccess()) {
            actions.add(small("Скинути пароль", () -> Confirm.ask("Скинути пароль?",
                    user.fullName() + " отримає тимчасовий пароль, а поточні сесії закриються.", "Скинути", "Ні", () -> resetPassword(user))));
            if (user.userId() != selfId) {
                var revoke = small("Забрати доступ", () -> Confirm.ask("Забрати доступ?",
                        user.fullName() + " більше не зможе входити в NewErp. На старому сайті нічого не зміниться.", "Забрати", "Ні",
                        () -> revoke(user)));
                revoke.addThemeVariants(ButtonVariant.LUMO_ERROR);
                actions.add(revoke);
            }
        }
        return actions;
    }

    private void showPassword(String username, String password) {
        if (password != null) {
            new TemporaryPasswordDialog(username, password).open();
        }
    }

    private void load() {
        users = service.list();
        show();
    }

    private void show() {
        grid.setItems(users.stream().filter(u -> !withAccessOnly.getValue() || u.hasAccess()).toList());
    }

    private static Button small(String text, Runnable action) {
        var button = new Button(text, e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return button;
    }
}
