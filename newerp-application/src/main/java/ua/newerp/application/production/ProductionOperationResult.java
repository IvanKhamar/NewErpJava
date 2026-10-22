package ua.newerp.application.production;

import java.util.List;

/** warnings: what was missing when writing off (what there was got written off). */
public record ProductionOperationResult(List<SizeChangeDto> sizes, List<String> warnings) {
}
