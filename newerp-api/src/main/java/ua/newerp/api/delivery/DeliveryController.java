package ua.newerp.api.delivery;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.delivery.CityDto;
import ua.newerp.application.delivery.CreateShipmentRequest;
import ua.newerp.application.delivery.DeliveryService;
import ua.newerp.application.delivery.ReminderDto;
import ua.newerp.application.delivery.ShipmentDto;
import ua.newerp.application.delivery.ShipmentFormDto;
import ua.newerp.application.delivery.ShipmentPrintKind;
import ua.newerp.application.delivery.ShipmentRepository;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.delivery.TrackingRefreshResult;
import ua.newerp.application.delivery.WarehouseDto;
import ua.newerp.application.delivery.WarehouseRefreshResult;

/** Nova Poshta delivery: an order's waybill, tracking, reminders. Access as to orders (Admin, Manager). */
@RestController
@Tag(name = "Доставка")
class DeliveryController {

    private final ShipmentRepository repository;
    private final ShipmentService shipments;
    private final DeliveryService delivery;

    DeliveryController(ShipmentRepository repository, ShipmentService shipments, DeliveryService delivery) {
        this.repository = repository;
        this.shipments = shipments;
        this.delivery = delivery;
    }

    @GetMapping("/orders/{id:\\d+}/shipment")
    @Operation(summary = "ТТН замовлення зі статусом (204 — ТТН ще немає)")
    ResponseEntity<ShipmentDto> shipment(@PathVariable int id) {
        return repository.getForOrder(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/orders/{id:\\d+}/shipment/form")
    @Operation(summary = "Форма нової ТТН, заповнена даними замовлення (місто й відділення — з довідника, якщо знайшлися)")
    ShipmentFormDto form(@PathVariable int id) {
        return shipments.form(id);
    }

    @PostMapping("/orders/{id:\\d+}/shipment")
    @Operation(summary = "Створити ТТН у Новій Пошті (без ключа API — лише тестову, якщо nova-poshta.fake-documents)")
    ShipmentDto create(@PathVariable int id, @RequestBody CreateShipmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return shipments.create(id, request, user.user().userId());
    }

    @DeleteMapping("/orders/{id:\\d+}/shipment")
    @Operation(summary = "Видалити ТТН у Новій Пошті й у нас")
    ResponseEntity<Void> delete(@PathVariable int id) {
        shipments.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** The PDF goes without a file name, so the browser opens it for printing instead of downloading it. */
    @GetMapping("/orders/{id:\\d+}/shipment/print")
    @Operation(summary = "PDF накладної (kind=Document) чи маркування (kind=Markings); ключ API лишається на сервері")
    ResponseEntity<byte[]> print(@PathVariable int id, @RequestParam(defaultValue = "Document") String kind) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(shipments.print(id, ShipmentPrintKind.parse(kind)).pdf());
    }

    @PostMapping("/delivery/tracking/refresh")
    @Operation(summary = "Оновити статуси незакритих ТТН у Нової Пошти (без ключа API нічого не запитує: clientConfigured = false)")
    TrackingRefreshResult refreshTracking() {
        return delivery.refreshTracking();
    }

    @GetMapping("/delivery/reminders")
    @Operation(summary = "Посилки на відділенні, у яких за 0–3 дні почнеться платне зберігання, а клієнту ще не нагадували")
    List<ReminderDto> reminders() {
        return delivery.reminders();
    }

    @PostMapping("/delivery/reminders/{orderId:\\d+}/done")
    @Operation(summary = "Позначити, що клієнту нагадали (зникне зі списку)")
    ResponseEntity<Void> reminded(@PathVariable int orderId) {
        delivery.markReminded(orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/delivery/cities")
    @Operation(summary = "Міста з довідника, назва яких починається з search")
    List<CityDto> cities(@RequestParam(required = false) String search) {
        return shipments.searchCities(search);
    }

    @GetMapping("/delivery/warehouses")
    @Operation(summary = "Робочі відділення й поштомати міста, у назві яких є search")
    List<WarehouseDto> warehouses(@RequestParam String cityRef, @RequestParam(required = false) String search) {
        return shipments.searchWarehouses(cityRef, search);
    }

    @PostMapping("/delivery/warehouses/refresh")
    @Operation(summary = "Завантажити свіжий довідник відділень з Нової Пошти (спільний з вітриною старого сайту)")
    WarehouseRefreshResult refreshWarehouses() {
        return shipments.refreshWarehouses();
    }
}
