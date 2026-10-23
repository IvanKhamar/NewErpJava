package ua.newerp.domain.locations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ua.newerp.domain.common.DomainException;

/** A shop's warehouse or pickup point, as "Каталог → Склади" of the old admin: name, contact, phone, city, address and type. */
public final class ShopLocation {

    /** The Excel file's columns in the same order as the old site's export. */
    public static final List<String> COLUMNS = List.of("ID", "Назва", "Опис", "Контакт", "Телефон", "Місто", "Адреса", "Тип");

    private ShopLocation() {
    }

    /** Checks the fields. The name is required, up to 64 characters, as in the old site's form; the rest within the columns. */
    public static ShopLocationData validate(String name, String description, String contact, String telephone, String city, String address,
            String type) {
        var data = new ShopLocationData(t(name), t(description), t(contact), t(telephone), t(city), t(address), t(type));
        if (data.name().isEmpty() || data.name().length() > 64) {
            throw new DomainException("Назва має містити від 1 до 64 символів.");
        }
        max(data.contact(), 128, "Контакт");
        max(data.telephone(), 255, "Телефон");
        max(data.city(), 128, "Місто");
        max(data.address(), 255, "Адреса");
        max(data.type(), 64, "Тип");
        max(data.description(), 5000, "Опис");
        return data;
    }

    /**
     * Parses the file's rows (the first is the header, empty ones are skipped). The old import wrote each row at once and without
     * checks, so an error in the middle of a file left half of the changes. Here the errors are collected for the whole file,
     * and if there is even one, nothing is written.
     */
    public static LocationImport parseImport(List<List<String>> rows) {
        var result = new ArrayList<LocationImportRow>();
        var errors = new ArrayList<String>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            if (IntStream.range(0, COLUMNS.size()).allMatch(c -> cell(cells, c).isBlank())) {
                continue;
            }

            int rowNumber = i + 1;
            String idText = cell(cells, 0).strip();
            if (!idText.isEmpty() && !idText.matches("\\d{1,9}")) {
                errors.add("Рядок " + rowNumber + ": ID «" + idText + "» має бути числом або порожнім.");
                continue;
            }
            try {
                ShopLocationData data = validate(cell(cells, 1), cell(cells, 2), cell(cells, 3), cell(cells, 4), cell(cells, 5), cell(cells, 6),
                        cell(cells, 7));
                result.add(new LocationImportRow(rowNumber, idText.isEmpty() ? 0 : Integer.parseInt(idText), data));
            } catch (DomainException e) {
                errors.add("Рядок " + rowNumber + ": " + e.getMessage());
            }
        }

        Map<Integer, List<Integer>> rowsById = new LinkedHashMap<>();
        result.stream().filter(r -> r.id() > 0).forEach(r -> rowsById.computeIfAbsent(r.id(), k -> new ArrayList<>()).add(r.rowNumber()));
        rowsById.forEach((id, numbers) -> {
            if (numbers.size() > 1) {
                errors.add("ID " + id + " повторюється в рядках " + numbers.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ".");
            }
        });
        if (result.isEmpty() && errors.isEmpty()) {
            errors.add("У файлі немає рядків для імпорту.");
        }
        return new LocationImport(result, errors);
    }

    private static String cell(List<String> cells, int index) {
        return index < cells.size() && cells.get(index) != null ? cells.get(index) : "";
    }

    private static String t(String value) {
        return value == null ? "" : value.strip();
    }

    private static void max(String value, int max, String field) {
        if (value.length() > max) {
            throw new DomainException(field + " довший за " + max + " символів.");
        }
    }
}
