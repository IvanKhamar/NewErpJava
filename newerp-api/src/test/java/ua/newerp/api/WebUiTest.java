package ua.newerp.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ua.newerp.IntegrationTest;

/** The UI folder is a temporary one with two invented files instead of a published Blazor app. */
class WebUiTest extends IntegrationTest {

    @DynamicPropertySource
    static void webRoot(DynamicPropertyRegistry registry) {
        registry.add("newerp.web-root", WebUiTest::createWebRoot);
    }

    static String createWebRoot() {
        try {
            Path root = Files.createTempDirectory("newerp-web");
            Files.writeString(root.resolve("index.html"), "<html><base href=\"/app/\">NewErp</html>");
            Files.createDirectories(root.resolve("_framework"));
            Files.writeString(root.resolve("_framework/blazor.webassembly.js"), "console.log('blazor')");
            return root.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void servesTheUiWithoutLogin() {
        // MockMvc does not follow a forward, it only records where it goes; a real server returns index.html
        assertThat(mvc.get().uri("/app/")).hasStatusOk().hasForwardedUrl("/app/index.html");
        assertThat(mvc.get().uri("/app/index.html")).hasStatusOk().bodyText().contains("NewErp");
        assertThat(mvc.get().uri("/app/_framework/blazor.webassembly.js")).hasStatusOk().bodyText().contains("blazor");
        assertThat(mvc.get().uri("/app")).hasStatus3xxRedirection();
    }

    @Test
    void pageAddressesGetIndexButMissingFilesDoNot() {
        assertThat(mvc.get().uri("/app/orders/1001")).hasStatusOk().bodyText().contains("NewErp");
        assertThat(mvc.get().uri("/app/_framework/missing.js")).hasStatus(404);
    }

    @Test
    void apiStillNeedsLogin() {
        assertThat(mvc.get().uri("/orders")).hasStatus(401);
    }
}
