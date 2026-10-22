package ua.newerp.api.orders;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.orders.AssignManagerRequest;
import ua.newerp.application.orders.ChangeOrderStatusRequest;
import ua.newerp.application.orders.ChangeOrderStatusResult;
import ua.newerp.application.orders.OrderDetails;
import ua.newerp.application.orders.OrderFilter;
import ua.newerp.application.orders.OrderListItem;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderService;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.application.sms.SendSmsRequest;
import ua.newerp.application.sms.SendSmsResultDto;
import ua.newerp.application.sms.SmsDto;
import ua.newerp.application.sms.SmsLog;
import ua.newerp.application.sms.SmsService;

/** Same routes as OrderEndpoints in .NET. A @RestController returns objects; Spring writes them as JSON. */
@RestController
@RequestMapping("/orders")
@Tag(name = "Замовлення")
class OrderController {

    private final OrderQueries queries;
    private final OrderService service;
    private final SmsService sms;
    private final SmsLog smsLog;

    OrderController(OrderQueries queries, OrderService service, SmsService sms, SmsLog smsLog) {
        this.queries = queries;
        this.service = service;
        this.sms = sms;
        this.smsLog = smsLog;
    }

    // No annotation on filter: Spring fills the record from the query string (?statusId=5&page=2), like [AsParameters].
    @GetMapping
    @Operation(summary = "Список замовлень з фільтрами і пагінацією")
    PagedResult<OrderListItem> list(OrderFilter filter) {
        return queries.list(filter);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Картка замовлення: товари та історія статусів")
    ResponseEntity<OrderDetails> get(@PathVariable int id) {
        return ResponseEntity.of(queries.get(id));
    }

    @GetMapping("/statuses")
    @Operation(summary = "Довідник статусів")
    List<OrderStatusDto> statuses() {
        return queries.listStatuses();
    }

    @GetMapping("/managers")
    @Operation(summary = "Кого можна призначити відповідальним (активні користувачі)")
    List<LookupItem> managers() {
        return queries.listManagers();
    }

    @GetMapping("/sources")
    @Operation(summary = "Довідник джерел замовлення")
    List<LookupItem> sources() {
        return queries.listSources();
    }

    @PutMapping("/{id:\\d+}/status")
    @Operation(summary = "Змінити статус (із записом у журнал). warnings — попередження від інших модулів")
    ChangeOrderStatusResult changeStatus(@PathVariable int id, @RequestBody ChangeOrderStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.changeStatus(id, request, user.user().userId());
    }

    @PutMapping("/{id:\\d+}/manager")
    @Operation(summary = "Призначити відповідального менеджера")
    ResponseEntity<Void> assignManager(@PathVariable int id, @RequestBody AssignManagerRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.assignManager(id, request, user.user().userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id:\\d+}/sms")
    @Operation(summary = "Надіслати SMS клієнту замовлення (зараз заглушка: нічого не надсилає, sent = false, але пише в журнал)")
    SendSmsResultDto sendSms(@PathVariable int id, @RequestBody SendSmsRequest request) {
        return sms.sendToOrder(id, request);
    }

    @GetMapping("/{id:\\d+}/sms")
    @Operation(summary = "SMS, надіслані клієнту цього замовлення")
    List<SmsDto> listSms(@PathVariable int id) {
        return smsLog.listForOrder(id);
    }
}
