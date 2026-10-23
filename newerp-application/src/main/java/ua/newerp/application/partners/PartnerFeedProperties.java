package ua.newerp.application.partners;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the "partner-feed" section of application.yml (PartnerFeedOptions in .NET): the feed header and the shop
 * address for photo links.
 *
 * @param storeUrl the shop's storefront address (https://…/). A photo in the feed is {storeUrl}image/{path}.
 */
@ConfigurationProperties("partner-feed")
public record PartnerFeedProperties(String shopName, String company, String storeUrl) {

    public PartnerFeedProperties {
        shopName = shopName == null ? "Vidro" : shopName;
        company = company == null ? "" : company;
        storeUrl = storeUrl == null ? "" : storeUrl;
    }
}
