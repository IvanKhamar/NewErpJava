package ua.newerp.application.catalog;

public record SetPinRequest(int type, String key, int position, int productId) {
}
