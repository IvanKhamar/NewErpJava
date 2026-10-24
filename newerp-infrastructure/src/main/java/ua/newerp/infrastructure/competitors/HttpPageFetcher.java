package ua.newerp.infrastructure.competitors;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ua.newerp.application.competitors.PageFetcher;
import ua.newerp.domain.competitors.CompetitorSite;

/**
 * Downloads a page with the JDK HttpClient. Redirects are followed by hand and only within the competitor's site, so a link can
 * never lead the server to another host; addresses of the local network are refused; the body is cut at 2 MB.
 */
@Component
class HttpPageFetcher implements PageFetcher {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final boolean allowLocal;

    HttpPageFetcher() {
        this(false);
    }

    /** allowLocal: for tests with a server on localhost. */
    HttpPageFetcher(boolean allowLocal) {
        this.allowLocal = allowLocal;
    }

    @Override
    public String fetch(URI page, String siteHost) {
        URI current = page;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!CompetitorSite.isOnSite(current, siteHost)) {
                throw new PageFetchException("Сторінка веде на інший сайт: " + current.getHost());
            }
            requirePublic(current);
            HttpResponse<InputStream> response = send(current);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new PageFetchException("Сайт переадресував без адреси (HTTP " + status + ")."));
                close(response);
                current = current.resolve(location);
                continue;
            }
            if (status != 200) {
                close(response);
                throw new PageFetchException("Сайт відповів HTTP " + status + ".");
            }
            return body(response);
        }
        throw new PageFetchException("Забагато переадресацій.");
    }

    private void requirePublic(URI uri) {
        if (allowLocal) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress()) {
                    throw new PageFetchException("Адреса сайту веде в локальну мережу.");
                }
            }
        } catch (UnknownHostException e) {
            throw new PageFetchException("Сайт " + uri.getHost() + " не знайдено.");
        }
    }

    private HttpResponse<InputStream> send(URI uri) {
        var request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; NewErp price check)")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new PageFetchException("Сайт недоступний: " + e.getClass().getSimpleName() + ".");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PageFetchException("Перевірку перервано.");
        } catch (IllegalArgumentException e) {
            throw new PageFetchException("Неправильна адреса сторінки.");
        }
    }

    private static String body(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes(MAX_BYTES);
            return new String(bytes, charset(response.headers().firstValue("Content-Type").orElse("")));
        } catch (IOException e) {
            throw new PageFetchException("Не вдалося прочитати сторінку.");
        }
    }

    private static Charset charset(String contentType) {
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static void close(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException e) {
            // nothing to do, the connection is dropped anyway
        }
    }
}
