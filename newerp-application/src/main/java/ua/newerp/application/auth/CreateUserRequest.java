package ua.newerp.application.auth;

/** No temporaryPassword: the system generates one and returns it once. */
public record CreateUserRequest(String username, String firstName, String lastName, String email, String role,
        String temporaryPassword) {
}
