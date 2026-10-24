package ua.newerp.application.delivery;

/** A customs code suggestion: label is the product and its subcategory in Ukrainian, nameEng the English product name. */
public record HsCodeDto(String hsCode, String label, String nameEng) {
}
