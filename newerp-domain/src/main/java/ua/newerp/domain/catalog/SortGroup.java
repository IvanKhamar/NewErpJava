package ua.newerp.domain.catalog;

/**
 * A group of products to put in order.
 *
 * @param name a name for people: "Сукні — Чорний", "Мітка: Хіт"
 */
public record SortGroup(SortGroupType type, String key, String name, Integer categoryId, Integer filterId, Integer labelId) {

    public static final String CROSS_SELL_KEY = "panel";

    /** The group_type value in the old site's pin table. */
    public String typeCode() {
        return type.code();
    }
}
