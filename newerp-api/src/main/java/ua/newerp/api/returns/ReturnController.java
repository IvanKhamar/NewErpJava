package ua.newerp.api.returns;

import java.net.URI;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.returns.AddReturnHistoryRequest;
import ua.newerp.application.returns.CreateReturnRequest;
import ua.newerp.application.returns.ReturnDetails;
import ua.newerp.application.returns.ReturnFilter;
import ua.newerp.application.returns.ReturnListItem;
import ua.newerp.application.returns.ReturnLookupsDto;
import ua.newerp.application.returns.ReturnService;

/** Product returns (Admin and Manager, see SecurityConfig). */
@RestController
@RequestMapping("/returns")
@Tag(name = "Повернення")
class ReturnController {

    private final ReturnService service;

    ReturnController(ReturnService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Повернення посторінково, нові зверху; фільтри: returnId, orderId, statusId, search (клієнт, телефон, товар, модель)")
    PagedResult<ReturnListItem> list(ReturnFilter filter) {
        return service.list(filter);
    }

    @GetMapping("/lookups")
    @Operation(summary = "Статуси, причини й дії повернень")
    ReturnLookupsDto lookups() {
        return service.lookups();
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Повернення з журналом статусів")
    ReturnDetails get(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Оформити повернення товару із замовлення: клієнт і товар беруться із замовлення, кількість — не більше, ніж ще можна повернути")
    ResponseEntity<Map<String, Integer>> create(@RequestBody CreateReturnRequest request) {
        int id = service.create(request);
        return ResponseEntity.created(URI.create("/returns/" + id)).body(Map.of("id", id));
    }

    @PostMapping("/{id:\\d+}/history")
    @Operation(summary = "Новий статус повернення з коментарем у журнал; actionId — змінити дію (повернено кошти, обмін…)")
    ResponseEntity<Void> addHistory(@PathVariable int id, @RequestBody AddReturnHistoryRequest request) {
        service.addHistory(id, request);
        return ResponseEntity.noContent().build();
    }
}
