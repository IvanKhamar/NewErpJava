package ua.newerp.application.reports;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The revenue feed for an external dashboard ("revenue-feed" in application.yml). The token goes into an env variable or
 * application-local.yml, never into application.yml; without one the feed is off.
 *
 * @param token   what the dashboard sends in the X-Feed-Token header (or ?token=)
 * @param maxDays the longest daily series one request may ask for
 */
@ConfigurationProperties("revenue-feed")
public record RevenueFeedProperties(String token, Integer maxDays) {

    public RevenueFeedProperties {
        token = token == null ? "" : token.strip();
        maxDays = maxDays == null ? 366 : maxDays;
    }

    public boolean enabled() {
        return !token.isEmpty();
    }
}
