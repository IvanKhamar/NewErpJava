package ua.newerp.infrastructure.auth;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Component;

import ua.newerp.application.auth.PasswordHasher;

/**
 * PBKDF2 hashes in exactly the format of ASP.NET Core Identity's PasswordHasher (version 3), so the .NET and Java
 * versions read each other's erp_user_login.password_hash and the same users log in to both.
 * <p>
 * Format, base64 of: 0x01 | PRF (uint32, 0 = SHA1, 1 = SHA256, 2 = SHA512) | iterations (uint32) | salt length (uint32)
 * | salt | subkey; numbers are big-endian. New hashes: SHA512, 100 000 iterations, 16-byte salt, 32-byte subkey
 * (the .NET 10 defaults).
 */
@Component
class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final int PRF_SHA512 = 2;
    private static final int ITERATIONS = 100_000;
    private static final int SALT_LENGTH = 16;
    private static final int SUBKEY_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] subkey = pbkdf2(PRF_SHA512, password, salt, ITERATIONS, SUBKEY_LENGTH);

        ByteBuffer out = ByteBuffer.allocate(13 + SALT_LENGTH + SUBKEY_LENGTH); // ByteBuffer is big-endian by default
        out.put((byte) 0x01).putInt(PRF_SHA512).putInt(ITERATIONS).putInt(SALT_LENGTH).put(salt).put(subkey);
        return Base64.getEncoder().encodeToString(out.array());
    }

    @Override
    public boolean verify(String passwordHash, String password) {
        try {
            ByteBuffer in = ByteBuffer.wrap(Base64.getDecoder().decode(passwordHash));
            if (in.get() != 0x01) {
                return false; // version 2 (0x00) is never written by NewErp
            }
            int prf = in.getInt();
            int iterations = in.getInt();
            int saltLength = in.getInt();
            if (saltLength < 16 || saltLength > in.remaining() - 16) {
                return false;
            }
            byte[] salt = new byte[saltLength];
            in.get(salt);
            byte[] expected = new byte[in.remaining()];
            in.get(expected);

            byte[] actual = pbkdf2(prf, password, salt, iterations, expected.length);
            // Constant-time comparison: an attacker cannot learn from timing how many bytes matched
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false; // not base64 or cut short: treat as a wrong password
        }
    }

    private static byte[] pbkdf2(int prf, String password, byte[] salt, int iterations, int length) {
        String algorithm = switch (prf) {
            case 0 -> "PBKDF2WithHmacSHA1";
            case 1 -> "PBKDF2WithHmacSHA256";
            case 2 -> "PBKDF2WithHmacSHA512";
            default -> throw new IllegalArgumentException("Unknown PRF " + prf);
        };
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, length * 8);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }
}
