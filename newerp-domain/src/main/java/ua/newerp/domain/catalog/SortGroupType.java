package ua.newerp.domain.catalog;

import java.util.Locale;

import ua.newerp.domain.common.DomainException;

/**
 * The kind of a product group inside which the order is counted. Sent as a number (ordinal), like the .NET enum.
 * code is the group_type value in the old site's pin table.
 */
public enum SortGroupType {

    /** A theme (a filter of the "themes" group); group_key is the filter's name. */
    TAG("tag"),

    /** A category without colors; group_key is the category id. */
    CATEGORY("category"),

    /** A category in one color; group_key is "categoryId_filterId". */
    CATEGORY_COLOR("category_color"),

    /** A label ("Новинка", "Хіт"); group_key is the label's name. */
    LABEL("label"),

    /** The cross-sell panel at checkout; group_key is "panel". Its order is stored apart from the product's sort_order. */
    CROSS_SELL("crosssell");

    private final String code;

    SortGroupType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SortGroupType fromCode(String code) {
        for (SortGroupType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new DomainException("Невідомий вид групи «" + code + "».");
    }

    public static SortGroupType fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new DomainException("Невідомий вид групи " + ordinal + ".");
        }
        return values()[ordinal];
    }

    /** As .NET reads an enum from a query string: the number or the name ("CategoryColor"), case aside. */
    public static SortGroupType parse(String value) {
        String text = value == null ? "" : value.strip();
        if (text.matches("\\d+")) {
            return fromOrdinal(Integer.parseInt(text));
        }
        String name = text.replace("_", "").toUpperCase(Locale.ROOT);
        for (SortGroupType type : values()) {
            if (type.name().replace("_", "").equals(name)) {
                return type;
            }
        }
        throw new DomainException("Невідомий вид групи «" + text + "».");
    }
}
