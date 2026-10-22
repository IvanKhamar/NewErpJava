package ua.newerp.application.auth;

import java.io.Serializable;

/** Login result: the user and the account version (security stamp) kept in the session. */
public record AuthenticatedUser(CurrentUser user, String securityStamp) implements Serializable {
}
