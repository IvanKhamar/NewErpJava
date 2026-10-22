package ua.newerp.application.production;

public record CategoryLinkRequest(int categoryId, String nameFilter, boolean matchExact) {
}
