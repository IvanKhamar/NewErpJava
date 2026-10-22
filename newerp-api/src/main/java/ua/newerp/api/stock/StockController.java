package ua.newerp.api.stock;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.stock.AdjustStockRequest;
import ua.newerp.application.stock.CreateStockCategoryRequest;
import ua.newerp.application.stock.CreateStockSupplierRequest;
import ua.newerp.application.stock.CreatedId;
import ua.newerp.application.stock.ReorderReportDto;
import ua.newerp.application.stock.ReorderService;
import ua.newerp.application.stock.SaveStockItemRequest;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockItemFilter;
import ua.newerp.application.stock.StockMovementDto;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StockService;
import ua.newerp.application.stock.StockSupplierDto;

/** Admin and Storekeeper (see SecurityConfig). Same routes as StockEndpoints in .NET. */
@RestController
@RequestMapping("/stock")
@Tag(name = "Склад: розхідники")
class StockController {

    private final StockRepository repository;
    private final StockService service;
    private final ReorderService reorder;

    StockController(StockRepository repository, StockService service, ReorderService reorder) {
        this.repository = repository;
        this.service = service;
        this.reorder = reorder;
    }

    @GetMapping("/categories")
    List<LookupItem> categories() {
        return repository.listCategories();
    }

    @PostMapping("/categories")
    ResponseEntity<CreatedId> createCategory(@RequestBody CreateStockCategoryRequest request) {
        return created("/stock/categories/", service.createCategory(request));
    }

    @GetMapping("/suppliers")
    List<StockSupplierDto> suppliers() {
        return repository.listSuppliers();
    }

    @PostMapping("/suppliers")
    ResponseEntity<CreatedId> createSupplier(@RequestBody CreateStockSupplierRequest request) {
        return created("/stock/suppliers/", service.createSupplier(request));
    }

    @GetMapping("/items")
    @Operation(summary = "Позиції складу; belowMinimumOnly=true показує те, що треба дозамовити")
    List<StockItemDto> items(StockItemFilter filter) {
        return repository.listItems(filter);
    }

    @GetMapping("/reorder")
    @Operation(summary = "Що дозамовити: позиції нижче мінімуму за постачальниками, до 2× мінімуму, з готовим текстом замовлення")
    ReorderReportDto reorder() {
        return reorder.getReport();
    }

    @GetMapping("/items/{id:\\d+}")
    ResponseEntity<StockItemDto> item(@PathVariable int id) {
        return ResponseEntity.of(repository.getItemDto(id));
    }

    @PostMapping("/items")
    ResponseEntity<CreatedId> createItem(@RequestBody SaveStockItemRequest request) {
        return created("/stock/items/", service.createItem(request));
    }

    @PutMapping("/items/{id:\\d+}")
    @Operation(summary = "Змінити позицію: назва, одиниця, категорія, постачальник, мінімум, ціна (залишок — лише через adjustments)")
    ResponseEntity<Void> updateItem(@PathVariable int id, @RequestBody SaveStockItemRequest request) {
        service.updateItem(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items/{id:\\d+}/adjustments")
    @Operation(summary = "Прихід (+) або витрата (-) з записом у журнал руху")
    StockMovementDto adjust(@PathVariable int id, @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.adjust(id, request, user.user().userId(), user.user().username());
    }

    @PutMapping("/items/{id:\\d+}/min-quantity")
    ResponseEntity<Void> setMinQuantity(@PathVariable int id, @RequestParam BigDecimal minQuantity) {
        service.setMinQuantity(id, minQuantity);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/items/{id:\\d+}/movements")
    @Operation(summary = "Журнал руху позиції")
    List<StockMovementDto> movements(@PathVariable int id) {
        return repository.listMovements(id);
    }

    static ResponseEntity<CreatedId> created(String path, int id) {
        return ResponseEntity.created(URI.create(path + id)).body(new CreatedId(id));
    }
}
