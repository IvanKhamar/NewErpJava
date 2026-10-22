package ua.newerp.api.auth;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.auth.UserLoginStore;

/**
 * On every request: did the account version change (role, password, access revoked) or was the user disabled
 * on the old site? Then the session is no longer valid and the request goes on as anonymous (→ 401).
 * Same as OnValidatePrincipal in the .NET version.
 */
class SecurityStampFilter extends OncePerRequestFilter {

    private final UserLoginStore store;

    SecurityStampFilter(UserLoginStore store) {
        this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            Optional<String> current = store.getActiveSecurityStamp(user.user().userId());
            if (current.isEmpty() || !current.get().equals(user.securityStamp())) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            }
        }
        chain.doFilter(request, response);
    }
}
