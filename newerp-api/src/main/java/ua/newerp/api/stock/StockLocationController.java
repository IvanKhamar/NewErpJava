package ua.newerp.api.stock;

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
import ua.newerp.application.stock.AdjustStockRequest;
import ua.newerp.application.stock.ItemPlacementDto;
import ua.newerp.application.stock.StockByWarehouseDto;
import ua.newerp.application.stock.StockLocationService;
import ua.newerp.application.stock.StockMovementDto;
import ua.newerp.application.stock.StockTransferDto;
import ua.newerp.application.stock.TransferStockRequest;

/** Consumables at several warehouses (Admin and Storekeeper, like the rest of /stock). */
@RestController
@RequestMapping("/stock")
@Tag(name = "Склад: розхідники")
class StockLocationController {

    private final StockLocationService service;

    StockLocationController(StockLocationService service) {
        this.service = service;
    }

    @GetMapping("/by-warehouse")
    @Operation(summary = "Залишки всіх позицій за складами: основний склад (те, що не лежить на інших) і кожен склад, "
            + "де щось є, з вартістю запасу на кожному")
    StockByWarehouseDto byWarehouse() {
        return service.byWarehouse();
    }

    @GetMapping("/items/{id:\\d+}/places")
    @Operation(summary = "Де лежить позиція: основний склад і склади з залишком")
    ItemPlacementDto places(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping("/items/{id:\\d+}/transfers")
    @Operation(summary = "Перемістити між складами (fromWarehouseId/toWarehouseId null — основний склад); загальний залишок "
            + "не змінюється, не більше, ніж є на складі-джерелі")
    ItemPlacementDto transfer(@PathVariable int id, @RequestBody TransferStockRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.transfer(id, request, user.user().userId(), user.user().username());
    }

    @GetMapping("/items/{id:\\d+}/transfers")
    @Operation(summary = "Переміщення позиції між складами, нові спершу")
    List<StockTransferDto> transfers(@PathVariable int id) {
        return service.transfers(id);
    }

    @PostMapping("/items/{id:\\d+}/warehouses/{warehouseId:\\d+}/adjustments")
    @Operation(summary = "Прихід (+) або витрата (-) на певному складі; іде в журнал руху з назвою складу. "
            + "Витрата — не більше, ніж є на цьому складі")
    StockMovementDto adjust(@PathVariable int id, @PathVariable int warehouseId, @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.adjust(id, warehouseId, request, user.user().userId(), user.user().username());
    }
}
