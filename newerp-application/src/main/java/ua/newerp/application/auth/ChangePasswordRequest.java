package ua.newerp.application.auth;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
