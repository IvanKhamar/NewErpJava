package ua.newerp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ApplicationIntegrationTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthIsUpWithDatabase() {
        assertThat(mvc.get().uri("/health"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }
}
