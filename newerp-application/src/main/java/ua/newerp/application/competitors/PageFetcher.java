package ua.newerp.application.competitors;

import java.net.URI;

/** Downloads a competitor's page. Redirects are followed only within the given site. */
public interface PageFetcher {

    /** The page's HTML; throws PageFetchException with a short reason for the manager when it cannot be read. */
    String fetch(URI page, String siteHost);

    class PageFetchException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public PageFetchException(String message) {
            super(message);
        }
    }
}
