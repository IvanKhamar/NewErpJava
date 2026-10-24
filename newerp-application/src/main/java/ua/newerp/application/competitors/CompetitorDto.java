package ua.newerp.application.competitors;

/** A competitor with how many product pages of it we watch. */
public record CompetitorDto(int id, String name, String siteUrl, String priceRegex, boolean active, int links) {
}
