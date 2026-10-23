package ua.newerp.application.partners;

import java.util.List;

/** A shop category and the sizes of its products (to pick in the mapping). */
public record ShopCategoryDto(int id, String name, List<String> sizes) {
}
