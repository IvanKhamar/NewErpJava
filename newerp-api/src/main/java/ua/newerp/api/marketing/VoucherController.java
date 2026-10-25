package ua.newerp.api.marketing;

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
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.marketing.SaveVoucherRequest;
import ua.newerp.application.marketing.VoucherCardDto;
import ua.newerp.application.marketing.VoucherService;
import ua.newerp.application.sms.SmsSendResult;

/** Gift vouchers (Admin and Manager, the /marketing/** rule in SecurityConfig). */
@RestController
@RequestMapping("/marketing/vouchers")
@Tag(name = "Маркетинг")
class VoucherController {

    private final VoucherService service;

    VoucherController(VoucherService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Подарункові сертифікати з залишком (новіші вгорі, до 500); search — код або ім'я")
    List<VoucherCardDto> list(@RequestParam(defaultValue = "") String search) {
        return service.list(search);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Сертифікат з історією списань")
    VoucherCardDto get(@PathVariable int id) {
        return service.get(id);
    }

    @GetMapping("/themes")
    @Operation(summary = "Теми (картинки) сертифікатів старого сайту")
    List<LookupItem> themes() {
        return service.themes();
    }

    @PostMapping
    @Operation(summary = "Новий сертифікат; порожній код згенерується (8 символів без 0/O й 1/I)")
    VoucherCardDto create(@RequestBody SaveVoucherRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Змінити сертифікат (історія списань лишається)")
    VoucherCardDto update(@PathVariable int id, @RequestBody SaveVoucherRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Видалити сертифікат, яким ще не платили (інакше — вимкнути)")
    ResponseEntity<Void> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** The phone is typed in each time: dd_voucher has no phone column. */
    record SendRequest(String phone) {
    }

    @PostMapping("/{id:\\d+}/sms")
    @Operation(summary = "Надіслати код і залишок отримувачу SMS (текст — vouchers.sms-text), із записом у журнал SMS")
    SmsSendResult sms(@PathVariable int id, @RequestBody SendRequest request) {
        return service.sendSms(id, request.phone());
    }
}
