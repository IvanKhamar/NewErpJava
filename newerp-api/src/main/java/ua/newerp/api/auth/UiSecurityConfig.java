package ua.newerp.api.auth;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextRepository;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import ua.newerp.application.auth.AuthService;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.auth.LoginRequest;
import ua.newerp.application.auth.UserLoginStore;
import ua.newerp.web.auth.LoginView;

/**
 * Security of the Vaadin UI under /ui/. A second filter chain, checked before the API's one (@Order(1)):
 * Vaadin's configurer sends anonymous users to the login page and checks @RolesAllowed on views.
 * The user lands in the same HTTP session as after POST /auth/login, so the session cookie works for both.
 */
@Configuration
class UiSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain uiSecurityFilterChain(HttpSecurity http, SecurityContextRepository contexts, UserLoginStore store)
            throws Exception {
        return http
                .securityMatcher("/ui/**")
                .securityContext(c -> c.securityContextRepository(contexts))
                .with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class))
                .addFilterBefore(new SecurityStampFilter(store), AuthorizationFilter.class)
                .build();
    }

    /** The login form's check: the same AuthService as POST /auth/login, the same principal in the session. */
    @Bean
    AuthenticationProvider loginFormAuthentication(AuthService auth) {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication request) {
                AuthenticatedUser user = auth.login(new LoginRequest(request.getName(), String.valueOf(request.getCredentials())))
                        .orElseThrow(() -> new BadCredentialsException("Невірний логін або пароль"));
                return UsernamePasswordAuthenticationToken.authenticated(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.user().role())));
            }

            @Override
            public boolean supports(Class<?> type) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
            }
        };
    }
}
