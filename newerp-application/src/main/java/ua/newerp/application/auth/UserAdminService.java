package ua.newerp.application.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;

/**
 * User management (Admin only): new employees, NewErp access, roles, password resets.
 * Every change issues a new account version, so the person's old sessions stop working at once.
 */
@Service
@Transactional
public class UserAdminService {

    private final UserLoginStore store;
    private final PasswordHasher hasher;
    private final Clock clock;

    public UserAdminService(UserLoginStore store, PasswordHasher hasher, Clock clock) {
        this.store = store;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return store.list();
    }

    /** New employee: a row in dd_user (shared with the old site) and a NewErp login with a temporary password. */
    public UserAccessResult create(CreateUserRequest request) {
        String username = requireText(request.username(), "Логін", 3, 20);
        NewUser user = new NewUser(
                username,
                requireText(request.firstName(), "Ім'я", 1, 32),
                requireText(request.lastName(), "Прізвище", 1, 32),
                request.email() == null ? "" : request.email().strip());
        if (user.email().length() > 96) {
            throw new DomainException("Email: не довше 96 символів.");
        }
        String role = requireRole(request.role());
        if (store.usernameTaken(username)) {
            throw new DomainException("Логін \"" + username + "\" вже зайнятий.");
        }
        String password = temporaryPassword(request.temporaryPassword(), username);

        int userId = store.createUser(user, LocalDateTime.now(clock));
        store.saveLogin(userId, role, hasher.hash(password), true);
        return new UserAccessResult(userId, password);
    }

    /**
     * Gives access to an existing user or changes the role. New access needs a password (generated if not given);
     * a role change keeps the password unless one is passed.
     */
    public UserAccessResult setAccess(int actorId, int userId, SetAccessRequest request) {
        UserDto user = store.get(userId).orElseThrow(() -> new NotFoundException("Користувача " + userId + " не знайдено."));
        String role = requireRole(request.role());
        if (userId == actorId && !Roles.ADMIN.equals(role)) {
            throw new DomainException("Не можна зняти роль Admin із себе: система може лишитися без адміністратора.");
        }

        String password = null;
        if (!user.hasAccess() || (request.temporaryPassword() != null && !request.temporaryPassword().isEmpty())) {
            password = temporaryPassword(request.temporaryPassword(), user.username());
        }
        store.saveLogin(userId, role, password == null ? null : hasher.hash(password), password == null ? null : true);
        return new UserAccessResult(userId, password);
    }

    /** Takes NewErp access away. On the old site the user stays as is. */
    public void revoke(int actorId, int userId) {
        if (userId == actorId) {
            throw new DomainException("Не можна забрати доступ у самого себе.");
        }
        if (!store.deleteLogin(userId)) {
            throw new NotFoundException("У користувача " + userId + " немає доступу до NewErp.");
        }
    }

    /** Resets the password to a temporary one; the person changes it after logging in. */
    public UserAccessResult resetPassword(int userId, ResetPasswordRequest request) {
        UserDto user = store.get(userId)
                .filter(UserDto::hasAccess)
                .orElseThrow(() -> new NotFoundException("У користувача " + userId + " немає доступу до NewErp."));
        String password = temporaryPassword(request.temporaryPassword(), user.username());
        store.saveLogin(userId, null, hasher.hash(password), true);
        return new UserAccessResult(userId, password);
    }

    private static String temporaryPassword(String requested, String username) {
        if (requested == null || requested.isEmpty()) {
            return PasswordPolicy.generate();
        }
        PasswordPolicy.require(requested, username);
        return requested;
    }

    private static String requireRole(String role) {
        String wanted = role == null ? "" : role.strip();
        return Roles.ALL.stream()
                .filter(r -> r.equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new DomainException("Роль має бути однією з: " + String.join(", ", Roles.ALL) + "."));
    }

    private static String requireText(String value, String field, int min, int max) {
        String text = value == null ? "" : value.strip();
        if (text.length() < min || text.length() > max) {
            throw new DomainException(field + ": від " + min + " до " + max + " символів.");
        }
        return text;
    }
}
