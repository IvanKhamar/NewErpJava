package ua.newerp.domain.locations;

/** A row of an import file: id 0 is a new record, otherwise an update of an existing one. */
public record LocationImportRow(int rowNumber, int id, ShopLocationData data) {
}
