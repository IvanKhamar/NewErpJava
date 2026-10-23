package ua.newerp.api.payments;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.payments.BankImportResult;
import ua.newerp.application.payments.BankPaymentDto;
import ua.newerp.application.payments.BankPaymentFilter;
import ua.newerp.application.payments.BankPaymentService;
import ua.newerp.application.payments.CreatePaymentLinkRequest;
import ua.newerp.application.payments.LinkPaymentRequest;
import ua.newerp.application.payments.LiqPayService;
import ua.newerp.application.payments.LiqPayStatusDto;
import ua.newerp.application.payments.PaymentLinkDto;
import ua.newerp.application.payments.PaymentSuggestionDto;

/**
 * Bank payments (PrivatBank statement) matched to orders, and LiqPay card payment links.
 * Admin and Manager (see SecurityConfig).
 */
@RestController
@Tag(name = "Оплати")
class PaymentController {

    private final BankPaymentService bank;
    private final LiqPayService liqPay;

    PaymentController(BankPaymentService bank, LiqPayService liqPay) {
        this.bank = bank;
        this.liqPay = liqPay;
    }

    @GetMapping({"/payments/bank", "/payments/bank/"})
    @Operation(summary = "Надходження з фільтрами (період, linked, замовлення, пошук, hidden), посторінково")
    PagedResult<BankPaymentDto> list(BankPaymentFilter filter) {
        return bank.list(filter);
    }

    @PostMapping("/payments/bank/import")
    @Operation(summary = "Завантажити виписку (типово вчора й сьогодні); без ключів банку нічого не запитує: clientConfigured = false")
    BankImportResult importStatement(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return bank.importStatement(from, to);
    }

    @GetMapping("/payments/bank/{id:\\d+}/suggestions")
    @Operation(summary = "Замовлення, яким імовірно належить платіж (за сумою, номером і телефоном у призначенні), найкращі першими")
    List<PaymentSuggestionDto> suggestions(@PathVariable int id, @RequestParam(required = false) String customer) {
        return bank.suggest(id, customer);
    }

    @PutMapping("/payments/bank/{id:\\d+}/order")
    @Operation(summary = "Прив'язати платіж до замовлення (orderId = 0 — відв'язати)")
    BankPaymentDto link(@PathVariable int id, @RequestBody LinkPaymentRequest request) {
        return bank.link(id, request);
    }

    @PutMapping("/payments/bank/{id:\\d+}/visible")
    @Operation(summary = "Приховати платіж (visible=false), що не стосується замовлень, або повернути його")
    ResponseEntity<Void> setVisibility(@PathVariable int id, @RequestParam boolean visible) {
        bank.setVisibility(id, visible);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders/{id:\\d+}/payments")
    @Operation(summary = "Банківські надходження, прив'язані до замовлення")
    List<BankPaymentDto> forOrder(@PathVariable int id) {
        return bank.forOrder(id);
    }

    @PostMapping("/orders/{id:\\d+}/liqpay-link")
    @Operation(summary = "Посилання на оплату LiqPay (amount не задано — уся сума замовлення); без ключів LiqPay посилання тестове: isTest = true")
    PaymentLinkDto liqPayLink(@PathVariable int id, @RequestBody CreatePaymentLinkRequest request) {
        return liqPay.createLink(id, request);
    }

    @GetMapping("/orders/{id:\\d+}/liqpay-status")
    @Operation(summary = "Чи оплачено замовлення через LiqPay (запит status за номером замовлення)")
    LiqPayStatusDto liqPayStatus(@PathVariable int id) {
        return liqPay.status(id);
    }
}
