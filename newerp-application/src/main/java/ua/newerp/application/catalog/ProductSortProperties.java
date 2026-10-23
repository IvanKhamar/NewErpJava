package ua.newerp.application.catalog;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the "product-sort" section of application.yml (ProductSortOptions in .NET). Values as in the old site's code.
 *
 * @param countedStatusIds       order statuses whose sales count
 * @param excludedUserIds        orders created by these admin users (dd_order.user_id) do not count
 * @param excludedOrderMarkers   orders with one of these strings in the first or last name, company or comment do not count (B2B)
 * @param excludedCommentMarkers orders with one of these strings in the comment do not count
 * @param colorFilterIds         color filters: a category with products in these colors is ordered for each color apart
 * @param tagFilterGroupId       the "themes" filter group (each filter of it is a product group)
 * @param newLabelName           the label whose products go from the newest, not by sales
 */
@ConfigurationProperties("product-sort")
public record ProductSortProperties(List<Integer> countedStatusIds, List<Integer> excludedUserIds, List<String> excludedOrderMarkers,
        List<String> excludedCommentMarkers, List<Integer> colorFilterIds, int tagFilterGroupId, String newLabelName, Integer defaultDays) {

    public ProductSortProperties {
        countedStatusIds = countedStatusIds == null ? List.of() : List.copyOf(countedStatusIds);
        excludedUserIds = excludedUserIds == null ? List.of() : List.copyOf(excludedUserIds);
        excludedOrderMarkers = excludedOrderMarkers == null ? List.of() : List.copyOf(excludedOrderMarkers);
        excludedCommentMarkers = excludedCommentMarkers == null ? List.of() : List.copyOf(excludedCommentMarkers);
        colorFilterIds = colorFilterIds == null ? List.of() : List.copyOf(colorFilterIds);
        newLabelName = newLabelName == null ? "Новинка" : newLabelName;
        defaultDays = defaultDays == null ? 90 : defaultDays;
    }
}
