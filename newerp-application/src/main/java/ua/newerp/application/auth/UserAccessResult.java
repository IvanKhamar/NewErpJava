package ua.newerp.application.auth;

/** temporaryPassword is shown only here, once; pass it to the person, they change it after logging in. */
public record UserAccessResult(int userId, String temporaryPassword) {
}
