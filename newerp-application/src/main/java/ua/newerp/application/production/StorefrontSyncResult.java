package ua.newerp.application.production;

/** models: how many models were passed; products: how many shop products were updated. */
public record StorefrontSyncResult(int models, int products) {
}
