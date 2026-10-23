package ua.newerp.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import ua.newerp.IntegrationTest;

/**
 * The Vaadin UI on a real server (MockMvc does not run the Vaadin servlet): the production bundle is in the jar,
 * the login page opens, and after an API login the order list opens with the same session cookie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql("/sql/users.sql")
class VaadinUiTest extends IntegrationTest {

    @LocalServerPort
    int port;

    // The cookie manager keeps the session cookie between requests, like a browser
    final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    @Test
    void loginPageAndOrderListOpen() throws IOException, InterruptedException {
        HttpResponse<String> anonymous = get("/ui/orders");
        assertThat(anonymous.statusCode()).isEqualTo(200);
        assertThat(anonymous.uri().getPath()).isEqualTo("/ui/login");
        assertThat(anonymous.body()).contains("window.Vaadin");

        HttpResponse<String> login = http.send(HttpRequest.newBuilder(url("/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"olena\",\"password\":\"" + PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);

        HttpResponse<String> orders = get("/ui/orders");
        assertThat(orders.statusCode()).isEqualTo(200);
        assertThat(orders.uri().getPath()).isEqualTo("/ui/orders");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(url(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
