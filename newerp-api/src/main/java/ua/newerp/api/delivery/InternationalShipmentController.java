package ua.newerp.api.delivery;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.delivery.CreateInternationalShipmentRequest;
import ua.newerp.application.delivery.HsCodeDto;
import ua.newerp.application.delivery.InternationalFormDto;
import ua.newerp.application.delivery.InternationalShipmentService;
import ua.newerp.application.delivery.ShipmentDto;

/**
 * Waybills abroad (Nova Post International), next to the domestic ones of DeliveryController: the order's waybill is one,
 * whichever carrier; it is read and deleted by the same /orders/{id}/shipment. Admin and Manager, see SecurityConfig.
 */
@RestController
@Tag(name = "Доставка")
class InternationalShipmentController {

    private final InternationalShipmentService service;

    InternationalShipmentController(InternationalShipmentService service) {
        this.service = service;
    }

    @GetMapping("/orders/{id:\\d+}/shipment/international/form")
    @Operation(summary = "Форма міжнародної накладної з даними замовлення; коди УКТЗЕД, англійські назви й вагу вводять вручну")
    InternationalFormDto form(@PathVariable int id) {
        return service.form(id);
    }

    @PostMapping("/orders/{id:\\d+}/shipment/international")
    @Operation(summary = "Створити накладну в Nova Post International (без ключа API — лише тестову, якщо "
            + "nova-post-international.fake-documents)")
    ShipmentDto create(@PathVariable int id, @RequestBody CreateInternationalShipmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(id, request, user.user().userId());
    }

    @GetMapping("/delivery/international/countries")
    @Operation(summary = "Країни отримувача (усі, крім України), за українською назвою")
    List<InternationalFormDto.Country> countries() {
        return service.countries();
    }

    @GetMapping("/delivery/international/hs-codes")
    @Operation(summary = "Підказки кодів УКТЗЕД за словом (від двох літер) для країни отримувача")
    List<HsCodeDto> hsCodes(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String country) {
        return service.hsCodes(search, country);
    }
}
