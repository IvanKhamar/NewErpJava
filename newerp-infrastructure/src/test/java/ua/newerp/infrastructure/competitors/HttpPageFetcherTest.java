package ua.newerp.infrastructure.competitors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ua.newerp.application.competitors.PageFetcher.PageFetchException;

/** Against a small web server on localhost with invented pages. */
class HttpPageFetcherTest {

    HttpServer server;
    String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/dress", e -> send(e, 200, "text/html; charset=windows-1251", "<b>Сукня 1 299 грн</b>"));
        server.createContext("/moved", e -> redirect(e, "/dress"));
        server.createContext("/away", e -> redirect(e, "http://127.0.0.1:" + server.getAddress().getPort() + "/dress"));
        server.createContext("/gone", e -> send(e, 404, "text/html", "нема"));
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsThePageInItsCharsetAndFollowsRedirectsOnTheSite() {
        var fetcher = new HttpPageFetcher(true);
        assertThat(fetcher.fetch(URI.create(base + "/dress"), "localhost")).isEqualTo("<b>Сукня 1 299 грн</b>");
        assertThat(fetcher.fetch(URI.create(base + "/moved"), "localhost")).contains("Сукня");
    }

    @Test
    void refusesOtherSitesErrorsAndTheLocalNetwork() {
        var fetcher = new HttpPageFetcher(true);
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/away"), "localhost")).isInstanceOf(PageFetchException.class)
                .hasMessageContaining("інший сайт");
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/gone"), "localhost")).hasMessage("Сайт відповів HTTP 404.");
        assertThatThrownBy(() -> new HttpPageFetcher().fetch(URI.create(base + "/dress"), "localhost"))
                .hasMessage("Адреса сайту веде в локальну мережу.");
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        Charset charset = type.contains("1251") ? Charset.forName("windows-1251") : Charset.forName("UTF-8");
        byte[] bytes = body.getBytes(charset);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
