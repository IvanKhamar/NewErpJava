package ua.newerp.application.payments;

/** Import totals. clientConfigured = false: no bank keys, nothing was loaded. */
public record BankImportResult(boolean clientConfigured, int fetched, int added, int skipped) {
}
