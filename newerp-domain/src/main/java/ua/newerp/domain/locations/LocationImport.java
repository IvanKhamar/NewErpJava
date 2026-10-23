package ua.newerp.domain.locations;

import java.util.List;

/** The parsed rows of an import file and the errors found in it. */
public record LocationImport(List<LocationImportRow> rows, List<String> errors) {
}
