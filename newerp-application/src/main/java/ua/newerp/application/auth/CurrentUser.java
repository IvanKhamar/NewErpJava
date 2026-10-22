package ua.newerp.application.auth;

import java.io.Serializable;

/**
 * The logged-in user. mustChangePassword: the password is temporary and has to be changed.
 * Serializable, because it lives in the HTTP session.
 */
public record CurrentUser(int userId, String username, String fullName, String role, boolean mustChangePassword)
        implements Serializable {
}
