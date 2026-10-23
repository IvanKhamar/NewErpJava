package ua.newerp.application.catalog;

/** @param type the SortGroupType ordinal: a number, like the .NET enum in JSON */
public record SortGroupDto(int type, String key, String name) {
}
