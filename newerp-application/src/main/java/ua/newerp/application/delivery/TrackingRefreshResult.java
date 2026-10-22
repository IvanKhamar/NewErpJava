package ua.newerp.application.delivery;

/** clientConfigured = false: the Nova Poshta API key is not set, so nothing was checked (expected locally). */
public record TrackingRefreshResult(boolean clientConfigured, int checked, int updated) {
}
