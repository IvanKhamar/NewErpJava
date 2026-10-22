package ua.newerp.application.auth;

/** Password hashing. The password itself is never stored, only its hash. */
public interface PasswordHasher {

    String hash(String password);

    boolean verify(String passwordHash, String password);
}
