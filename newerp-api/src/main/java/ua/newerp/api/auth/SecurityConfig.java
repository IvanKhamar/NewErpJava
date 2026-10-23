package ua.newerp.api.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import ua.newerp.application.auth.Roles;
import ua.newerp.application.auth.UserLoginStore;

/**
 * Who may call what. The same rules as AuthSetup + Policies in the .NET version:
 * Orders, Finance (cash register, payments) → Admin, Manager; FinanceSetup (cashiers, warehouses, requisites) → Admin;
 * Stock → Admin, Storekeeper; Bonuses, Users → Admin; everything else needs a login. The first matching rule wins.
 * <p>
 * After POST /auth/login the user is kept in the HTTP session; the browser sends the session cookie (newerp.auth)
 * with every request. Spring Security is a chain of servlet filters in front of the controllers.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts, UserLoginStore store)
            throws Exception {
        return http
                // CSRF: the session cookie is SameSite=Strict, so other sites cannot send it (the .NET version relies on the same)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .securityContext(c -> c.securityContextRepository(contexts))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/health", "/error", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/app/**",
                                "/", "/favicon.ico", "/styles/**", "/lumo/**")
                        .permitAll()
                        // The partner's site fetches its feed without a login, as on the old site.
                        .requestMatchers(HttpMethod.GET, "/feeds/*.xml").permitAll()
                        .requestMatchers(HttpMethod.POST, "/cashbox/cashiers").hasRole(Roles.ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/cashbox/cashiers/*").hasRole(Roles.ADMIN)
                        .requestMatchers("/cashbox/warehouses/**", "/cashbox/requisites/**").hasRole(Roles.ADMIN)
                        .requestMatchers("/orders/**", "/delivery/**", "/cashbox/**", "/payments/**", "/preorders/**",
                                "/partners/**", "/catalog/sort/**", "/customers/**", "/returns/**",
                                "/locations/**", "/reports/**", "/marketing/**", "/sms/**").hasAnyRole(Roles.ADMIN, Roles.MANAGER)
                        .requestMatchers("/stock/**", "/production/**").hasAnyRole(Roles.ADMIN, Roles.STOREKEEPER)
                        .requestMatchers("/staff/bonuses", "/users/**").hasRole(Roles.ADMIN)
                        // Everything else needs a login, so a new endpoint cannot stay open by accident.
                        .anyRequest().authenticated())
                // An API answers 401 instead of redirecting to a login page; 403 for a wrong role is the default.
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new SecurityStampFilter(store), AuthorizationFilter.class)
                .build();
    }
}
