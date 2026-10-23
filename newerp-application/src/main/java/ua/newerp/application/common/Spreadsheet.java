package ua.newerp.application.common;

import java.util.List;

/** Reading and writing Excel (.xlsx) files: the warehouse directory, stocktaking sheets. */
public interface Spreadsheet {

    /** One sheet with a bold header row. Numbers go in as numbers, everything else as text. */
    byte[] write(String sheetName, List<String> header, List<List<Object>> rows);

    /** The first sheet's rows as text; DomainException when it is not .xlsx. */
    List<List<String>> read(byte[] file);
}
