package ua.newerp.application.stock;

import java.util.List;

/** What an uploaded sheet would change. lines: every counted item; errors: rows that cannot be used (nothing is written). */
public record StocktakingPreviewDto(List<StocktakingLineDto> lines, List<String> errors) {
}
