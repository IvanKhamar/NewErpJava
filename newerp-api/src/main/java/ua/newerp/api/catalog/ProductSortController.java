package ua.newerp.api.catalog;

import java.time.LocalDate;
import java.util.List;

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
import ua.newerp.application.catalog.ApplySortRequest;
import ua.newerp.application.catalog.CrossSellMetricDto;
import ua.newerp.application.catalog.ProductSortService;
import ua.newerp.application.catalog.RecalculateGroupRequest;
import ua.newerp.application.catalog.SalesPeriod;
import ua.newerp.application.catalog.SetPinRequest;
import ua.newerp.application.catalog.SortGroupDto;
import ua.newerp.application.catalog.SortPinDto;
import ua.newerp.application.catalog.SortPreviewRow;
import ua.newerp.application.catalog.SortProductDto;
import ua.newerp.application.catalog.SortResult;
import ua.newerp.application.catalog.SortSnapshotDto;
import ua.newerp.domain.catalog.SortGroupType;

/** The order of products on the storefront by sales (Admin and Manager, see SecurityConfig). */
@RestController
@RequestMapping("/catalog/sort")
@Tag(name = "Порядок товарів")
class ProductSortController {

    private final ProductSortService service;

    ProductSortController(ProductSortService service) {
        this.service = service;
    }

    @GetMapping("/groups")
    @Operation(summary = "Групи товарів у порядку перерахунку: теми, категорії (з кольорами окремо), мітки, панель допродажу")
    List<SortGroupDto> groups() {
        return service.listGroups();
    }

    @GetMapping("/preview")
    @Operation(summary = "Новий порядок групи без запису: продажі за період (days або from–to), закріплені товари, поточна й нова позиція")
    List<SortPreviewRow> preview(@RequestParam String type, @RequestParam String key, @RequestParam(required = false) Integer days,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return service.preview(SortGroupType.parse(type), key, new SalesPeriod(days, from, to));
    }

    @PostMapping("/apply")
    @Operation(summary = "Записати готовий порядок групи (зі знімком попереднього)")
    SortResult apply(@RequestBody ApplySortRequest request) {
        return service.apply(request);
    }

    @PostMapping("/recalculate")
    @Operation(summary = "Перерахувати й записати одну групу")
    SortResult recalculate(@RequestBody RecalculateGroupRequest request) {
        return service.recalculateGroup(request);
    }

    @PostMapping("/recalculate-all")
    @Operation(summary = "Перерахувати всі групи (один знімок перед початком)")
    SortResult recalculateAll(@RequestBody(required = false) SalesPeriod period) {
        return service.recalculateAll(period);
    }

    @GetMapping("/snapshots")
    @Operation(summary = "Останні 50 знімків порядку")
    List<SortSnapshotDto> snapshots() {
        return service.listSnapshots();
    }

    @PostMapping("/snapshots/{id:\\d+}/restore")
    @Operation(summary = "Повернути порядок товарів зі знімка")
    ResponseEntity<Void> restore(@PathVariable int id) {
        service.restoreSnapshot(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pins")
    @Operation(summary = "Усі закріплені вручну позиції")
    List<SortPinDto> pins() {
        return service.listPins();
    }

    @PostMapping("/pins")
    @Operation(summary = "Закріпити товар на позиції в групі (попередній пін товару чи позиції замінюється)")
    ResponseEntity<Void> setPin(@RequestBody SetPinRequest request) {
        service.setPin(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/pins/{id:\\d+}")
    ResponseEntity<Void> removePin(@PathVariable int id) {
        service.removePin(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/crosssell-metric")
    CrossSellMetricDto crossSellMetric() {
        return service.getCrossSellMetric();
    }

    @PutMapping("/crosssell-metric")
    @Operation(summary = "Популярність у панелі допродажу: panelOnly=true — лише продажі через панель")
    CrossSellMetricDto setCrossSellMetric(@RequestBody CrossSellMetricDto metric) {
        return service.setCrossSellMetric(metric);
    }

    @GetMapping("/products")
    @Operation(summary = "Пошук увімкнених товарів за назвою (для закріплення)")
    List<SortProductDto> products(@RequestParam(required = false) String search) {
        return service.searchProducts(search);
    }
}
