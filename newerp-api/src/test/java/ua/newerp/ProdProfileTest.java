package ua.newerp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/** The app starts with the production profile, and that profile really turns on its safety settings. */
@ActiveProfiles("prod")
class ProdProfileTest extends IntegrationTest {

    @Autowired
    Environment environment;

    @Test
    void secureCookieAndNoApiDocs() {
        assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(404);
        assertThat(mvc.get().uri("/health")).hasStatusOk();
    }
}
