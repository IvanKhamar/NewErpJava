package ua.newerp.api.stock;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.stock.CreatePurchaseOrderRequest;
import ua.newerp.application.common.CreatedId;
import ua.newerp.application.stock.PurchaseOrderDto;
import ua.newerp.application.stock.PurchaseOrderFilter;
import ua.newerp.application.stock.PurchaseOrderRepository;
import ua.newerp.application.stock.PurchaseOrderService;
import ua.newerp.application.stock.PurchaseOrderSummaryDto;
import ua.newerp.application.stock.ReceivePurchaseOrderRequest;
import ua.newerp.application.stock.SetPurchaseOrderLinesRequest;

@RestController
@RequestMapping("/stock/purchase-orders")
@Tag(name = "Склад: замовлення постачальникам")
class PurchaseOrderController {

    private final PurchaseOrderRepository repository;
    private final PurchaseOrderService service;

    PurchaseOrderController(PurchaseOrderRepository repository, PurchaseOrderService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Замовлення постачальникам; status = draft, ordered, received або cancelled")
    List<PurchaseOrderSummaryDto> list(PurchaseOrderFilter filter) {
        return repository.list(filter);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Замовлення з рядками, отриманим і готовим текстом для постачальника")
    ResponseEntity<PurchaseOrderDto> get(@PathVariable int id) {
        return ResponseEntity.of(repository.getDto(id));
    }

    @PostMapping
    @Operation(summary = "Створити чернетку вручну")
    ResponseEntity<CreatedId> create(@RequestBody CreatePurchaseOrderRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return StockController.created("/stock/purchase-orders/", service.create(request, user.user().userId()));
    }

    @PostMapping("/from-reorder/{supplierId:\\d+}")
    @Operation(summary = "Створити чернетку для постачальника з того, що пропонує звіт «що дозамовити»")
    ResponseEntity<CreatedId> createFromReorder(@PathVariable int supplierId, @AuthenticationPrincipal AuthenticatedUser user) {
        return StockController.created("/stock/purchase-orders/", service.createFromReorder(supplierId, user.user().userId()));
    }

    @PutMapping("/{id:\\d+}/lines")
    @Operation(summary = "Змінити рядки чернетки")
    ResponseEntity<Void> setLines(@PathVariable int id, @RequestBody SetPurchaseOrderLinesRequest request) {
        service.setLines(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id:\\d+}/ordered")
    @Operation(summary = "Позначити, що замовлення надіслано постачальнику")
    ResponseEntity<Void> markOrdered(@PathVariable int id) {
        service.markOrdered(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id:\\d+}/receive")
    @Operation(summary = "Прийняти товар (можна частинами): залишки зростають, у журналі складу — прихід")
    ResponseEntity<Void> receive(@PathVariable int id, @RequestBody ReceivePurchaseOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.receive(id, request, user.user().userId(), user.user().username());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id:\\d+}/cancel")
    ResponseEntity<Void> cancel(@PathVariable int id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
