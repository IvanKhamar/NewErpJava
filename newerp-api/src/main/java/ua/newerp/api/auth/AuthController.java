package ua.newerp.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ua.newerp.application.auth.AuthService;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.auth.ChangePasswordRequest;
import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.auth.LoginRequest;

@RestController
@RequestMapping("/auth")
@Tag(name = "Вхід")
class AuthController {

    private final AuthService service;
    private final SessionLogin session;

    AuthController(AuthService service, SessionLogin session) {
        this.service = service;
        this.session = session;
    }

    @PostMapping("/login")
    @Operation(summary = "Вхід: видає cookie сесії")
    ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return service.login(request)
                .<ResponseEntity<?>>map(user -> {
                    session.signIn(user, http, response);
                    return ResponseEntity.ok(user.user());
                })
                .orElseGet(() -> {
                    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
                    problem.setTitle("Невірний логін або пароль");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
                });
    }

    @PostMapping("/logout")
    @Operation(summary = "Вихід: видаляє cookie сесії")
    ResponseEntity<Void> logout(HttpServletRequest http) {
        session.signOut(http);
        return ResponseEntity.noContent().build();
    }

    // @AuthenticationPrincipal gives the object we put into the session at login (ClaimsPrincipal in .NET)
    @GetMapping("/me")
    @Operation(summary = "Хто зараз увійшов; mustChangePassword = true — пароль тимчасовий, його треба змінити")
    CurrentUser me(@AuthenticationPrincipal AuthenticatedUser user) {
        return user.user();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Змінити власний пароль (потрібен поточний). Інші сесії цього користувача закриваються")
    ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest http, HttpServletResponse response) {
        AuthenticatedUser updated = service.changePassword(user.user().userId(), request);
        // The new account version invalidates all sessions, so the current one is issued again.
        session.signIn(updated, http, response);
        return ResponseEntity.noContent().build();
    }
}
