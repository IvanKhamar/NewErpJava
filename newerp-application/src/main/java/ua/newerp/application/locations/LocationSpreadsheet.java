package ua.newerp.application.locations;

import java.util.List;

/** Reading and writing the Excel (.xlsx) file of the directory. */
public interface LocationSpreadsheet {

    byte[] write(List<String> header, List<LocationDto> rows);

    /** The first sheet's rows as text; DomainException when it is not .xlsx. */
    List<List<String>> read(byte[] file);
}
