package ua.newerp.api.auth;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import ua.newerp.application.auth.AuthenticatedUser;

/** Puts the logged-in user into the session and takes them out (SignInAsync / SignOutAsync in .NET). */
@Component
class SessionLogin {

    private final SecurityContextRepository contexts;

    SessionLogin(SecurityContextRepository contexts) {
        this.contexts = contexts;
    }

    void signIn(AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {
        // New session id on login: a session id known before login (session fixation) becomes useless.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.user().role()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    void signOut(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
