package ua.newerp.application.auth;

/** Account for checking a login: data from dd_user plus password hash and role from erp_user_login. */
public record UserLoginRecord(int userId, String username, String fullName, boolean active, String passwordHash,
        String role, String securityStamp, boolean mustChangePassword) {
}
