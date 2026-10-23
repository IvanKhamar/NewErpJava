package ua.newerp.web.auth;

import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * The login page. The form posts the login and password to Spring Security (POST login), which checks them with
 * AuthService, puts the user into the session and opens the page the user wanted, as the Blazor login page does.
 */
@Route(value = "login", autoLayout = false)
@PageTitle("Вхід — NewErp")
@AnonymousAllowed
public class LoginView extends Main implements BeforeEnterObserver {

    private final LoginForm form = new LoginForm();

    public LoginView() {
        addClassNames(LumoUtility.Display.FLEX, LumoUtility.JustifyContent.CENTER, LumoUtility.AlignItems.CENTER,
                LumoUtility.Height.FULL);
        form.setAction("login");
        form.setForgotPasswordButtonVisible(false);
        form.setI18n(i18n());
        add(form);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Security sends the user back to login?error when the password is wrong
        form.setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }

    private static LoginI18n i18n() {
        var i18n = new LoginI18n();
        var texts = new LoginI18n.Form();
        texts.setTitle("Вхід у NewErp");
        texts.setUsername("Логін");
        texts.setPassword("Пароль");
        texts.setSubmit("Увійти");
        var error = new LoginI18n.ErrorMessage();
        error.setTitle("Невірний логін або пароль");
        error.setMessage("Перевірте логін і пароль і спробуйте ще раз.");
        error.setUsername("Вкажіть логін");
        error.setPassword("Вкажіть пароль");
        i18n.setForm(texts);
        i18n.setErrorMessage(error);
        return i18n;
    }
}
