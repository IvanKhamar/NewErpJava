package ua.newerp.application.delivery;

/** clientConfigured = false: no API key, the directory was not changed. */
public record WarehouseRefreshResult(boolean clientConfigured, int count) {
}
