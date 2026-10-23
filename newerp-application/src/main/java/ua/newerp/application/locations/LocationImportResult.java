package ua.newerp.application.locations;

import java.util.List;

/** The import's outcome: how many were added and updated, or the errors (then nothing is written). */
public record LocationImportResult(int added, int updated, List<String> errors) {
}
