package ua.newerp.web.auth;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;
import com.vaadin.flow.spring.security.AuthenticationContext;

import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.auth.CurrentUser;

/**
 * The logged-in user of the UI. The login is the same HTTP session as the API's, so one login works for both.
 * A Spring bean; views get it through their constructor (like @inject in Blazor).
 */
@Component
public class CurrentSession {

    private final AuthenticationContext authentication;
    private final SecurityContextRepository contexts;

    public CurrentSession(AuthenticationContext authentication, SecurityContextRepository contexts) {
        this.authentication = authentication;
        this.contexts = contexts;
    }

    public Optional<CurrentUser> user() {
        return authentication.getAuthenticatedUser(AuthenticatedUser.class).map(AuthenticatedUser::user);
    }

    public boolean hasAnyRole(String... roles) {
        return user().map(u -> Arrays.asList(roles).contains(u.role())).orElse(false);
    }

    /** After a password change the account version is new: the session gets the updated user, or it would be dropped. */
    public void replace(AuthenticatedUser user) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, current.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, VaadinServletRequest.getCurrent().getHttpServletRequest(),
                VaadinServletResponse.getCurrent().getHttpServletResponse());
    }

    public void logout() {
        authentication.logout();
    }
}
