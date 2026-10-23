package ua.newerp.application.production;

import java.util.List;

/** cut and sewn: sizes whose stock changed; components: how many component stocks changed. */
public record ProductionStocktakingResult(List<SizeChangeDto> cut, List<SizeChangeDto> sewn, int components) {
}
