package ua.newerp.application.locations;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.Spreadsheet;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.locations.LocationImport;
import ua.newerp.domain.locations.LocationImportRow;
import ua.newerp.domain.locations.ShopLocation;
import ua.newerp.domain.locations.ShopLocationData;

/** The directory of warehouses and pickup points (dd_warehouse) with Excel import and export, as "Каталог → Склади" of the old admin. */
@Service
@Transactional
public class LocationService {

    public static final int MAX_IMPORT_BYTES = 5 * 1024 * 1024;

    private final LocationStore store;
    private final Spreadsheet spreadsheet;

    public LocationService(LocationStore store, Spreadsheet spreadsheet) {
        this.store = store;
        this.spreadsheet = spreadsheet;
    }

    @Transactional(readOnly = true)
    public List<LocationDto> list(String search) {
        return store.list(search == null || search.isBlank() ? null : search.strip());
    }

    @Transactional(readOnly = true)
    public LocationDto get(int id) {
        return store.get(id).orElseThrow(() -> notFound(id));
    }

    public int create(SaveLocationRequest request) {
        return store.add(validate(request));
    }

    public void update(int id, SaveLocationRequest request) {
        if (!store.update(id, validate(request))) {
            throw notFound(id);
        }
    }

    public void delete(int id) {
        if (!store.delete(id)) {
            throw notFound(id);
        }
    }

    @Transactional(readOnly = true)
    public byte[] export() {
        return spreadsheet.write("Склади", ShopLocation.COLUMNS, store.list(null).stream()
                .map(x -> Arrays.<Object>asList(x.id(), x.name(), x.description(), x.contact(), x.telephone(), x.city(), x.address(), x.type()))
                .toList());
    }

    /** A row with the id of an existing record updates it, the rest are added (as the old import). With errors nothing is written. */
    public LocationImportResult importFile(byte[] file) {
        if (file.length > MAX_IMPORT_BYTES) {
            throw new DomainException("Файл більший за 5 МБ.");
        }
        LocationImport parsed = ShopLocation.parseImport(spreadsheet.read(file));
        if (!parsed.errors().isEmpty()) {
            return new LocationImportResult(0, 0, parsed.errors());
        }

        Set<Integer> existing = store.existingIds(parsed.rows().stream().map(LocationImportRow::id).filter(id -> id > 0).toList());
        int added = 0;
        int updated = 0;
        for (LocationImportRow row : parsed.rows()) {
            if (existing.contains(row.id())) {
                store.update(row.id(), row.data());
                updated++;
            } else {
                // An id that is not in the database is not recreated: the record gets a new number, as in the old import.
                store.add(row.data());
                added++;
            }
        }
        return new LocationImportResult(added, updated, List.of());
    }

    private static ShopLocationData validate(SaveLocationRequest r) {
        return ShopLocation.validate(r.name(), r.description(), r.contact(), r.telephone(), r.city(), r.address(), r.type());
    }

    private static NotFoundException notFound(int id) {
        return new NotFoundException("Склад " + id + " не знайдено.");
    }
}
