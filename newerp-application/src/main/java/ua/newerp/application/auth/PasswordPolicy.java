package ua.newerp.application.auth;

import java.security.SecureRandom;

import ua.newerp.domain.common.DomainException;

/** Password requirements and generation of temporary passwords. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    // No look-alike characters (0/O, 1/l/I), so a temporary password is easy to dictate or copy.
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    public static void require(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new DomainException("Пароль має містити щонайменше " + MIN_LENGTH + " символів.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new DomainException("Пароль не довший за " + MAX_LENGTH + " символів.");
        }
        if (password.equalsIgnoreCase(username)) {
            throw new DomainException("Пароль не може збігатися з логіном.");
        }
    }

    /** A random password from a cryptographically strong generator (SecureRandom = RandomNumberGenerator). */
    public static String generate() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
