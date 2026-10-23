package ua.newerp.api.production;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.production.ProductionLocationService;
import ua.newerp.application.production.SewnByWarehouseDto;
import ua.newerp.application.production.SewnTransferDto;
import ua.newerp.application.production.SewnTransferRequest;
import ua.newerp.application.production.SizePlacesDto;

/** Sewn pieces at several warehouses (Admin and Storekeeper, like the rest of /production). */
@RestController
@RequestMapping("/production")
@Tag(name = "Виробництво")
class SewnLocationController {

    private final ProductionLocationService service;

    SewnLocationController(ProductionLocationService service) {
        this.service = service;
    }

    @GetMapping("/by-warehouse")
    @Operation(summary = "Пошите кожного розміру активних моделей за складами: основний склад і кожен склад, де щось є")
    SewnByWarehouseDto byWarehouse() {
        return service.byWarehouse();
    }

    @GetMapping("/models/{id:\\d+}/places")
    @Operation(summary = "Де лежить пошите кожного розміру моделі: основний склад і склади з залишком")
    List<SizePlacesDto> places(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping("/models/{id:\\d+}/transfers")
    @Operation(summary = "Перемістити пошите розміру між складами (null — основний склад); пошитий залишок і вітрина не змінюються")
    List<SizePlacesDto> transfer(@PathVariable int id, @RequestBody SewnTransferRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.transfer(id, request, user.user().userId(), user.user().username());
    }

    @GetMapping("/models/{id:\\d+}/transfers")
    @Operation(summary = "Переміщення пошитого між складами, нові спершу")
    List<SewnTransferDto> transfers(@PathVariable int id) {
        return service.transfers(id);
    }
}
