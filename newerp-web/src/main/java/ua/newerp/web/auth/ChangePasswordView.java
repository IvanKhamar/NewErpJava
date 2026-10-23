package ua.newerp.web.auth;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;
import ua.newerp.application.auth.AuthService;
import ua.newerp.application.auth.ChangePasswordRequest;
import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.auth.PasswordPolicy;
import ua.newerp.domain.common.DomainException;
import ua.newerp.web.ui.Messages;

/** Change of one's own password. With a temporary password from the admin it is the only page that opens. */
@Route("change-password")
@PageTitle("Зміна пароля — NewErp")
@PermitAll
public class ChangePasswordView extends VerticalLayout {

    final PasswordField current = new PasswordField("Поточний пароль");
    final PasswordField password = new PasswordField("Новий пароль");
    final PasswordField repeat = new PasswordField("Новий пароль ще раз");
    final Button submit = new Button("Змінити");

    private final AuthService auth;
    private final CurrentSession session;

    public ChangePasswordView(AuthService auth, CurrentSession session) {
        this.auth = auth;
        this.session = session;
        setMaxWidth("480px");

        add(new H2("Зміна пароля"));
        if (session.user().map(CurrentUser::mustChangePassword).orElse(false)) {
            add(new Paragraph("Ви увійшли з тимчасовим паролем. Задайте власний, щоб продовжити роботу."));
        }
        password.setHelperText("Щонайменше " + PasswordPolicy.MIN_LENGTH + " символів, не такий, як логін");
        for (PasswordField field : new PasswordField[] {current, password, repeat}) {
            field.setRequired(true);
            field.setWidthFull();
        }
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.addClickListener(e -> change());
        add(current, password, repeat, submit);
    }

    private void change() {
        if (!password.getValue().equals(repeat.getValue())) {
            Messages.error("Нові паролі не збігаються.");
            return;
        }
        int userId = session.user().orElseThrow().userId();
        try {
            session.replace(auth.changePassword(userId, new ChangePasswordRequest(current.getValue(), password.getValue())));
        } catch (DomainException e) {
            Messages.error(e.getMessage());
            return;
        }
        Messages.success("Пароль змінено.");
        UI.getCurrent().navigate("");
    }
}
