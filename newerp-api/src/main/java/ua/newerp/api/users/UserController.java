package ua.newerp.api.users;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.auth.CreateUserRequest;
import ua.newerp.application.auth.ResetPasswordRequest;
import ua.newerp.application.auth.SetAccessRequest;
import ua.newerp.application.auth.UserAccessResult;
import ua.newerp.application.auth.UserAdminService;
import ua.newerp.application.auth.UserDto;

/** Admin only (see SecurityConfig). */
@RestController
@RequestMapping("/users")
@Tag(name = "Користувачі")
class UserController {

    private final UserAdminService service;

    UserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Усі користувачі старого сайту (dd_user): чи є доступ до NewErp, роль, останній вхід")
    List<UserDto> list() {
        return service.list();
    }

    @PostMapping
    @Operation(summary = "Новий працівник: рядок у dd_user (без входу в стару адмінку) і доступ до NewErp з тимчасовим паролем")
    ResponseEntity<UserAccessResult> create(@RequestBody CreateUserRequest request) {
        UserAccessResult result = service.create(request);
        return ResponseEntity.created(URI.create("/users/" + result.userId())).body(result);
    }

    @PutMapping("/{id:\\d+}/access")
    @Operation(summary = "Видати доступ наявному користувачу або змінити роль (Admin, Manager, Storekeeper)")
    UserAccessResult setAccess(@PathVariable int id, @RequestBody SetAccessRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.setAccess(user.user().userId(), id, request);
    }

    @DeleteMapping("/{id:\\d+}/access")
    @Operation(summary = "Забрати доступ до NewErp (на старому сайті користувач лишається)")
    ResponseEntity<Void> revoke(@PathVariable int id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.revoke(user.user().userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id:\\d+}/reset-password")
    @Operation(summary = "Скинути пароль на тимчасовий (повертається один раз)")
    UserAccessResult resetPassword(@PathVariable int id, @RequestBody ResetPasswordRequest request) {
        return service.resetPassword(id, request);
    }
}
