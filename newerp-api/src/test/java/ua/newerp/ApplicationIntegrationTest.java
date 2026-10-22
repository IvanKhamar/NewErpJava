package ua.newerp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationIntegrationTest extends IntegrationTest {

    @Test
    void healthIsUpWithDatabaseAndOpenWithoutLogin() {
        assertThat(mvc.get().uri("/health"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }
}
