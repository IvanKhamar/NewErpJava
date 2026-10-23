package ua.newerp.api.cashbox;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.cashbox.AutoCloseResult;
import ua.newerp.application.cashbox.CashboxService;
import ua.newerp.application.cashbox.CashboxSetupDto;
import ua.newerp.application.cashbox.ChequeDto;
import ua.newerp.application.cashbox.ChequeFilter;
import ua.newerp.application.cashbox.IssueChequeRequest;
import ua.newerp.application.cashbox.ReportTextDto;
import ua.newerp.application.cashbox.SaveCashierRequest;
import ua.newerp.application.cashbox.SaveDirectoryEntryRequest;
import ua.newerp.application.cashbox.WorkShiftDto;
import ua.newerp.application.cashbox.WorkShiftFilter;
import ua.newerp.application.cashbox.ZReportDto;
import ua.newerp.application.common.CreatedId;
import ua.newerp.application.common.PagedResult;

/**
 * The Checkbox cash register: cashiers, shifts, receipts for orders, X and Z reports.
 * Admin and Manager; editing cashiers, warehouses and requisites only Admin (see SecurityConfig).
 */
@RestController
@Tag(name = "Каса")
class CashboxController {

    private final CashboxService service;

    CashboxController(CashboxService service) {
        this.service = service;
    }

    @GetMapping("/cashbox/setup")
    @Operation(summary = "Касири (з відкритою зміною, якщо є), склади й реквізити; fakeMode — працює тестова заглушка Checkbox")
    CashboxSetupDto setup() {
        return service.setup();
    }

    @PostMapping("/cashbox/cashiers")
    @Operation(summary = "Новий касир (ключі Checkbox задаються в налаштуваннях, checkbox.cashiers)")
    CreatedId createCashier(@RequestBody SaveCashierRequest request) {
        return new CreatedId(service.createCashier(request));
    }

    @PutMapping("/cashbox/cashiers/{id:\\d+}")
    @Operation(summary = "Змінити касира")
    ResponseEntity<Void> updateCashier(@PathVariable int id, @RequestBody SaveCashierRequest request) {
        service.updateCashier(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cashbox/warehouses")
    @Operation(summary = "Новий склад (точка продажу)")
    CreatedId createWarehouse(@RequestBody SaveDirectoryEntryRequest request) {
        return new CreatedId(service.createWarehouse(request));
    }

    @PutMapping("/cashbox/warehouses/{id:\\d+}")
    @Operation(summary = "Змінити склад")
    ResponseEntity<Void> updateWarehouse(@PathVariable int id, @RequestBody SaveDirectoryEntryRequest request) {
        service.updateWarehouse(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cashbox/warehouses/{id:\\d+}")
    @Operation(summary = "Видалити склад, якщо на нього ніхто не посилається")
    ResponseEntity<Void> deleteWarehouse(@PathVariable int id) {
        service.deleteWarehouse(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cashbox/requisites")
    @Operation(summary = "Нові реквізити (ФОП)")
    CreatedId createRequisites(@RequestBody SaveDirectoryEntryRequest request) {
        return new CreatedId(service.createRequisites(request));
    }

    @PutMapping("/cashbox/requisites/{id:\\d+}")
    @Operation(summary = "Змінити реквізити")
    ResponseEntity<Void> updateRequisites(@PathVariable int id, @RequestBody SaveDirectoryEntryRequest request) {
        service.updateRequisites(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cashbox/requisites/{id:\\d+}")
    @Operation(summary = "Видалити реквізити, якщо на них ніхто не посилається")
    ResponseEntity<Void> deleteRequisites(@PathVariable int id) {
        service.deleteRequisites(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cashbox/shifts")
    @Operation(summary = "Зміни (останні 500) з кількістю й сумою фіскалізованих чеків")
    List<WorkShiftDto> shifts(WorkShiftFilter filter) {
        return service.shifts(filter);
    }

    @PostMapping("/cashbox/cashiers/{id:\\d+}/shift/open")
    @Operation(summary = "Відкрити зміну касира (якщо в Checkbox вона вже відкрита — взяти її)")
    WorkShiftDto openShift(@PathVariable int id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.openShift(id, user.user().userId());
    }

    @PostMapping("/cashbox/cashiers/{id:\\d+}/shift/close")
    @Operation(summary = "Закрити зміну (Z-звіт). 202 — Checkbox ще закриває зміну, звіт запише звірка")
    ResponseEntity<ZReportDto> closeShift(@PathVariable int id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.closeShift(id, user.user().userId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }

    @PostMapping("/cashbox/shifts/{id:\\d+}/sync")
    @Operation(summary = "Звірити зміну з Checkbox: якщо каса закрила її сама, записати Z-звіт (204 — зміна ще відкрита)")
    ResponseEntity<ZReportDto> syncShift(@PathVariable int id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.syncShift(id, user.user().userId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/cashbox/shifts/auto-close")
    @Operation(summary = "Те саме, що робить фонова задача: звірити відкриті зміни й закрити ті, у яких настав час автозакриття")
    AutoCloseResult autoClose() {
        return service.autoClose();
    }

    @PostMapping("/cashbox/cashiers/{id:\\d+}/x-report")
    @Operation(summary = "X-звіт (підсумок зміни без закриття) текстом")
    ReportTextDto xReport(@PathVariable int id) {
        return service.xReport(id);
    }

    @GetMapping("/cashbox/reports")
    @Operation(summary = "Z-звіти (останні 500)")
    List<ZReportDto> reports(@RequestParam(required = false) Integer cashierId, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return service.reports(cashierId, from, to);
    }

    @GetMapping("/cashbox/reports/{id:\\d+}/text")
    @Operation(summary = "Текст Z-звіту з Checkbox")
    ReportTextDto zReportText(@PathVariable int id) {
        return service.zReportText(id);
    }

    @GetMapping("/cashbox/cheques")
    @Operation(summary = "Чеки з фільтрами (касир, замовлення, період, fiscalized), посторінково")
    PagedResult<ChequeDto> cheques(ChequeFilter filter) {
        return service.cheques(filter);
    }

    @PostMapping("/cashbox/cheques/{id:\\d+}/fiscalize")
    @Operation(summary = "Повторити фіскалізацію чека, якого Checkbox не прийняв")
    ChequeDto fiscalize(@PathVariable int id) {
        return service.fiscalize(id);
    }

    @DeleteMapping("/cashbox/cheques/{id:\\d+}")
    @Operation(summary = "Видалити нефіскалізований чек")
    ResponseEntity<Void> deleteCheque(@PathVariable int id) {
        service.deleteCheque(id);
        return ResponseEntity.noContent().build();
    }

    /** The .NET routes are "/orders/{id}/cheque/" with a trailing slash; both forms are accepted here. */
    @GetMapping({"/orders/{id:\\d+}/cheque", "/orders/{id:\\d+}/cheque/"})
    @Operation(summary = "Чек замовлення (204 — чека ще немає)")
    ResponseEntity<ChequeDto> chequeForOrder(@PathVariable int id) {
        return service.chequeForOrder(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping({"/orders/{id:\\d+}/cheque", "/orders/{id:\\d+}/cheque/"})
    @Operation(summary = "Видати фіскальний чек (касир — вказаний або за кодом оплати; сума — мінус завдаток)")
    ChequeDto issueCheque(@PathVariable int id, @RequestBody IssueChequeRequest request) {
        return service.issueForOrder(id, request);
    }
}
