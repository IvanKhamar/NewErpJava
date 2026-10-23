package ua.newerp.domain.stock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import ua.newerp.domain.common.DomainException;

/**
 * Stocktaking of consumables: the storekeeper downloads the list, writes the counted quantities into the last column
 * and uploads it back. The old CRM did this through Excel import and export.
 */
public final class Stocktaking {

    public static final List<String> COLUMNS = List.of("ID", "Назва", "Категорія", "Од.", "За обліком", "Фактично");

    /** Quantities are stored with 3 decimals (decimal(12,3)). */
    public static final int SCALE = 3;

    /** A counted quantity of one item. */
    public record Count(int itemId, BigDecimal actual) {
    }

    public record Parsed(List<Count> counts, List<String> errors) {
    }

    private Stocktaking() {
    }

    /**
     * Reads the sheet: the ID column and the last ("Фактично") column. A row with an empty count was not counted and is skipped;
     * the header row is skipped when its first cell is "ID". Errors name the sheet's row number as Excel shows it.
     */
    public static Parsed parse(List<List<String>> rows) {
        var counts = new ArrayList<Count>();
        var errors = new ArrayList<String>();
        var seen = new HashSet<Integer>();
        int actualColumn = COLUMNS.size() - 1;
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String id = cell(row, 0);
            String actual = cell(row, actualColumn);
            if (r == 0 && id.equalsIgnoreCase("ID") || actual.isEmpty()) {
                continue;
            }
            int line = r + 1;
            Integer itemId = parseId(id);
            if (itemId == null) {
                errors.add("Рядок " + line + ": немає ID позиції або він не число.");
                continue;
            }
            BigDecimal quantity = parseQuantity(actual);
            if (quantity == null) {
                errors.add("Рядок " + line + ": «" + actual + "» — не кількість.");
            } else if (!valid(quantity)) {
                errors.add("Рядок " + line + ": кількість має бути від 0, не більше " + SCALE + " знаків після коми.");
            } else if (!seen.add(itemId)) {
                errors.add("Рядок " + line + ": позиція " + itemId + " указана двічі.");
            } else {
                counts.add(new Count(itemId, quantity));
            }
        }
        return new Parsed(counts, errors);
    }

    static void requireValid(BigDecimal actual) {
        if (actual == null || !valid(actual)) {
            throw new DomainException("Фактична кількість має бути від 0, не більше " + SCALE + " знаків після коми.");
        }
    }

    private static boolean valid(BigDecimal quantity) {
        return quantity.signum() >= 0 && quantity.stripTrailingZeros().scale() <= SCALE;
    }

    private static String cell(List<String> row, int index) {
        return index < row.size() && row.get(index) != null ? row.get(index).strip() : "";
    }

    private static Integer parseId(String text) {
        try {
            int id = Integer.parseInt(text);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Excel in Ukrainian shows "12,5"; spaces split thousands ("1 250"). */
    private static BigDecimal parseQuantity(String text) {
        try {
            return new BigDecimal(text.replace(",", ".").replace(" ", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
