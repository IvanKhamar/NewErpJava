package ua.newerp.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Pbkdf2PasswordHasherTest {

    final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void verifiesIdentityV3Hashes() {
        // Built by the ASP.NET Identity v3 layout with a fixed salt 00..0f: SHA512 / 100 000 and SHA256 / 10 000
        assertThat(hasher.verify("AQAAAAIAAYagAAAAEAABAgMEBQYHCAkKCwwNDg9tHDBVNhTVA+6E1t6Gpb7bZypsIX6UNUiGvKx4ddfq6Q==", "Passw0rd!")).isTrue();
        assertThat(hasher.verify("AQAAAAEAACcQAAAAEAABAgMEBQYHCAkKCwwNDg84iY7IYpC8DxYwg3xbC5yVRkUPKSkJj9HFY4sFM57x+A==", "secret")).isTrue();
        assertThat(hasher.verify("AQAAAAIAAYagAAAAEAABAgMEBQYHCAkKCwwNDg9tHDBVNhTVA+6E1t6Gpb7bZypsIX6UNUiGvKx4ddfq6Q==", "passw0rd!")).isFalse();
    }

    @Test
    void hashRoundTripWithRandomSalt() {
        String first = hasher.hash("Пароль123");
        String second = hasher.hash("Пароль123");

        assertThat(first).isNotEqualTo(second).startsWith("AQAAAAIAAYag"); // v3, SHA512, 100 000 iterations
        assertThat(hasher.verify(first, "Пароль123")).isTrue();
        assertThat(hasher.verify(first, "Пароль124")).isFalse();
    }

    @Test
    void garbageIsJustWrong() {
        assertThat(hasher.verify("", "x")).isFalse();
        assertThat(hasher.verify("not base64!", "x")).isFalse();
        assertThat(hasher.verify("AQAAAAI=", "x")).isFalse();
    }
}
