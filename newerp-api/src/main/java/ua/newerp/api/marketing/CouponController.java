package ua.newerp.api.marketing;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
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
import ua.newerp.application.marketing.CouponDto;
import ua.newerp.application.marketing.CouponService;
import ua.newerp.application.marketing.SaveCouponRequest;

/** Discount coupons of the shop (Admin and Manager, as all of /marketing). */
@RestController
@RequestMapping("/marketing/coupons")
@Tag(name = "Маркетинг")
class CouponController {

    private final CouponService service;

    CouponController(CouponService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Купони, новіші першими, з кількістю замовлень і сумою знижки; state = Active, Disabled, NotStarted, Expired, UsedUp")
    List<CouponDto> list(@RequestParam(required = false) String state) {
        return service.list(state);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Один купон")
    CouponDto get(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Новий купон: type P (відсоток) або F (сума), usesTotal і usesPerCustomer 0 — без обмеження")
    ResponseEntity<CouponDto> create(@RequestBody SaveCouponRequest request) {
        CouponDto coupon = service.create(request);
        return ResponseEntity.created(URI.create("/marketing/coupons/" + coupon.id())).body(coupon);
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Змінити купон; active = false вимикає його (купони не видаляються, замовлення на них посилаються)")
    CouponDto update(@PathVariable int id, @RequestBody SaveCouponRequest request) {
        return service.update(id, request);
    }
}
