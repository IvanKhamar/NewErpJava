package ua.newerp.application.auth;

import java.time.LocalDateTime;

/**
 * hasAccess: can log in to NewErp. activeOnOldSite is dd_user.status: a user disabled on the old site
 * cannot log in to NewErp either.
 */
public record UserDto(int userId, String username, String fullName, String email, boolean activeOnOldSite,
        boolean hasAccess, String role, LocalDateTime lastLoginAt) {
}
