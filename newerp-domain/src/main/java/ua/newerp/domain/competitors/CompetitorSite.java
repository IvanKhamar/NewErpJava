package ua.newerp.domain.competitors;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import ua.newerp.domain.common.DomainException;

/** Web addresses of competitors: only http(s), and a product page must be on the competitor's own site. */
public final class CompetitorSite {

    private CompetitorSite() {
    }

    /** A trimmed absolute http(s) address with a host, at most maxLength characters. */
    public static URI requireUrl(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            throw new DomainException(field + ": вкажіть адресу до " + maxLength + " символів.");
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new DomainException(field + ": адреса має починатися з http:// або https://.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new DomainException(field + ": це не схоже на адресу сторінки.");
        }
    }

    /** The host without "www.", lower case: shop.ua for https://www.Shop.ua/catalog. */
    public static String host(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /** Whether the page is on the site: the same host or its subdomain (m.shop.ua on shop.ua). */
    public static boolean isOnSite(URI page, String siteHost) {
        String host = host(page);
        return host.equals(siteHost) || host.endsWith("." + siteHost);
    }
}
