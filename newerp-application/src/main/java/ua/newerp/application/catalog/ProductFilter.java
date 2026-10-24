package ua.newerp.application.catalog;

/**
 * Query string filters of the shop product list (?categoryId=30&amp;enabled=true&amp;page=2).
 *
 * @param search  a name or model part, or a product number
 * @param enabled true: only switched on, false: only switched off, null: all
 */
public record ProductFilter(String search, Integer categoryId, Integer labelId, Boolean enabled, Integer page, Integer pageSize) {
}
