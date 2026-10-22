package ua.newerp.application.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.domain.common.DomainException;

/** Login and changing one's own password. */
@Service
@Transactional
public class AuthService {

    private final UserLoginStore store;
    private final PasswordHasher hasher;
    private final Clock clock;

    public AuthService(UserLoginStore store, PasswordHasher hasher, Clock clock) {
        this.store = store;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * Returns the user if login and password are right and the account is active, otherwise empty.
     * The reason is deliberately not given, so we do not hint which logins exist.
     */
    public Optional<AuthenticatedUser> login(LoginRequest request) {
        if (request.username() == null || request.username().isBlank() || request.password() == null || request.password().isEmpty()) {
            return Optional.empty();
        }
        Optional<UserLoginRecord> found = store.findByUsername(request.username().strip());
        if (found.isEmpty() || !found.get().active() || !hasher.verify(found.get().passwordHash(), request.password())) {
            return Optional.empty();
        }
        UserLoginRecord user = found.get();
        store.setLastLogin(user.userId(), LocalDateTime.now(clock));
        return Optional.of(new AuthenticatedUser(
                new CurrentUser(user.userId(), user.username(), user.fullName(), user.role(), user.mustChangePassword()),
                user.securityStamp()));
    }

    /**
     * Changes one's own password (the current one is required) and clears the "temporary password" flag.
     * The new account version logs out all other sessions of this user.
     */
    public AuthenticatedUser changePassword(int userId, ChangePasswordRequest request) {
        UserLoginRecord user = store.findById(userId).orElseThrow(() -> new DomainException("Обліковий запис не знайдено."));
        String current = request.currentPassword() == null ? "" : request.currentPassword();
        if (!hasher.verify(user.passwordHash(), current)) {
            throw new DomainException("Поточний пароль неправильний.");
        }
        if (current.equals(request.newPassword())) {
            throw new DomainException("Новий пароль має відрізнятися від поточного.");
        }
        PasswordPolicy.require(request.newPassword(), user.username());

        String stamp = store.saveLogin(userId, null, hasher.hash(request.newPassword()), false);
        return new AuthenticatedUser(new CurrentUser(user.userId(), user.username(), user.fullName(), user.role(), false), stamp);
    }
}
