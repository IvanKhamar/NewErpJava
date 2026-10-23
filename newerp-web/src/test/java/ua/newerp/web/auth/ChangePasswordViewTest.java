package ua.newerp.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;

import ua.newerp.application.auth.AuthService;
import ua.newerp.application.auth.ChangePasswordRequest;
import ua.newerp.application.auth.CurrentUser;
import ua.newerp.domain.common.DomainException;

// Views are created by hand with mocked services, so no routes are scanned (Vaadin could not create them itself).
@ViewPackages(packages = "ua.newerp.web.noroutes")
class ChangePasswordViewTest extends BrowserlessTest {

    final AuthService auth = mock(AuthService.class);
    final CurrentSession session = mock(CurrentSession.class);
    ChangePasswordView view;

    @BeforeEach
    void open() {
        when(session.user()).thenReturn(Optional.of(new CurrentUser(7, "olena", "Олена Коваль", "Manager", true)));
        view = new ChangePasswordView(auth, session);
        UI.getCurrent().add(view);
    }

    @Test
    void temporaryPasswordIsExplained() {
        assertThat(find(Paragraph.class, view).single().getText()).contains("тимчасовим паролем");
    }

    @Test
    void differentNewPasswordsAreNotSent() {
        test(view.current).setValue("Passw0rd!");
        test(view.password).setValue("NewPassw0rd!");
        test(view.repeat).setValue("Other");
        test(view.submit).click();

        verify(auth, never()).changePassword(anyInt(), any());
        assertThat(test(find(Notification.class).single()).getText()).isEqualTo("Нові паролі не збігаються.");
    }

    @Test
    void serviceErrorIsShown() {
        when(auth.changePassword(7, new ChangePasswordRequest("wrong", "NewPassw0rd!")))
                .thenThrow(new DomainException("Поточний пароль неправильний."));
        test(view.current).setValue("wrong");
        test(view.password).setValue("NewPassw0rd!");
        test(view.repeat).setValue("NewPassw0rd!");
        test(view.submit).click();

        assertThat(test(find(Notification.class).single()).getText()).isEqualTo("Поточний пароль неправильний.");
        verify(session, never()).replace(any());
    }
}
