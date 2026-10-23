package ua.newerp.domain.partners;

import java.util.List;

public record PartnerFeed(List<FeedCategory> categories, List<FeedOffer> offers) {
}
