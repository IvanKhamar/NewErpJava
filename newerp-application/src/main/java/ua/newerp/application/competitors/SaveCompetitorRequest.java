package ua.newerp.application.competitors;

/** priceRegex: optional, the first group is the price; used before the standard markup. */
public record SaveCompetitorRequest(String name, String siteUrl, String priceRegex, boolean active) {
}
