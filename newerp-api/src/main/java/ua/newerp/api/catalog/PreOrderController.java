package ua.newerp.api.catalog;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.catalog.PreOrderFilter;
import ua.newerp.application.catalog.PreOrderRow;
import ua.newerp.application.catalog.PreOrderService;
import ua.newerp.application.catalog.PreOrderSettleResult;
import ua.newerp.application.catalog.UpdatePreOrderRequest;

/** Pre-order quotas of product sizes. Admin and Manager (see SecurityConfig). */
@RestController
@RequestMapping("/preorders")
@Tag(name = "Передзамовлення")
class PreOrderController {

    private final PreOrderService service;

    PreOrderController(PreOrderService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Розміри товарів (до 200) з залишком, лімітом передзамовлення, очікуваною датою й тим, як їх продає вітрина")
    List<PreOrderRow> list(PreOrderFilter filter) {
        return service.list(filter);
    }

    @PutMapping("/{productOptionValueId:\\d+}")
    @Operation(summary = "Ліміт передзамовлення розміру (0 — не продавати наперед) і дата, коли розмір очікується")
    ResponseEntity<Void> update(@PathVariable int productOptionValueId, @RequestBody UpdatePreOrderRequest request) {
        service.update(productOptionValueId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/settle")
    @Operation(summary = "Списати продане наперед (мінусовий залишок) з лімітів; фонова задача робить це сама що settle-interval-minutes")
    PreOrderSettleResult settle() {
        return service.settle();
    }
}
