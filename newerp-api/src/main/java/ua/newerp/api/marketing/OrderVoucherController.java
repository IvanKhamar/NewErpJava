package ua.newerp.api.marketing;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.marketing.OrderVoucherDto;
import ua.newerp.application.marketing.VoucherService;

/**
 * A gift voucher paying part of an order. Separate endpoints, so the order and composition requests stay as in the .NET API.
 * Admin and Manager (the /orders/** rule in SecurityConfig).
 */
@RestController
@Tag(name = "Маркетинг")
class OrderVoucherController {

    private final VoucherService service;

    OrderVoucherController(VoucherService service) {
        this.service = service;
    }

    /** amount null: as much as the voucher's balance and the order allow. */
    record RedeemRequest(String code, BigDecimal amount) {
    }

    @GetMapping("/orders/{id:\\d+}/voucher")
    @Operation(summary = "Сертифікат, списаний у замовленні (204, якщо немає)")
    ResponseEntity<OrderVoucherDto> get(@PathVariable int id) {
        return service.forOrder(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/orders/{id:\\d+}/voucher")
    @Operation(summary = "Оплатити частину замовлення сертифікатом: рядок підсумку voucher, історія сертифіката, менша сума замовлення")
    OrderVoucherDto redeem(@PathVariable int id, @RequestBody RedeemRequest request) {
        return service.redeem(id, request.code(), request.amount());
    }

    @DeleteMapping("/orders/{id:\\d+}/voucher")
    @Operation(summary = "Зняти сертифікат із замовлення: гроші повертаються на сертифікат, сума замовлення — як була")
    ResponseEntity<Void> release(@PathVariable int id) {
        service.release(id);
        return ResponseEntity.noContent().build();
    }
}
