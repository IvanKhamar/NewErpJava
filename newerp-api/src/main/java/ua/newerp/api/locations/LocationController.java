package ua.newerp.api.locations;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationImportResult;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.locations.SaveLocationRequest;

/** Warehouses and pickup points (Admin and Manager, see SecurityConfig). */
@RestController
@RequestMapping("/locations")
@Tag(name = "Склади й пункти видачі")
class LocationController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final LocationService service;

    LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Довідник складів і пунктів видачі; search — назва, місто, адреса, тип")
    List<LocationDto> list(@RequestParam(required = false) String search) {
        return service.list(search);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Склад чи пункт видачі")
    LocationDto get(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Новий склад чи пункт видачі")
    ResponseEntity<Map<String, Integer>> create(@RequestBody SaveLocationRequest request) {
        int id = service.create(request);
        return ResponseEntity.created(URI.create("/locations/" + id)).body(Map.of("id", id));
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Змінити склад чи пункт видачі")
    ResponseEntity<Void> update(@PathVariable int id, @RequestBody SaveLocationRequest request) {
        service.update(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Видалити склад чи пункт видачі")
    ResponseEntity<Void> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export.xlsx")
    @Operation(summary = "Вивантажити довідник у Excel (колонки як у старого сайту: ID, Назва, Опис, Контакт, Телефон, Місто, Адреса, Тип)")
    ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("sklady-" + LocalDate.now() + ".xlsx").build().toString())
                .body(service.export());
    }

    // The file comes as the raw request body (application/octet-stream), not a form: no form token is needed, and a browser
    // on another site does not send such a request without a CORS permission.
    @PostMapping(path = "/import", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Імпорт з Excel: рядок з ID наявного запису оновлює його, решта додаються; з помилками нічого не записується")
    LocationImportResult importFile(@RequestBody byte[] file) {
        return service.importFile(file);
    }
}
