package ua.newerp.api.orders;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.orders.OrderDetails;
import ua.newerp.application.orders.OrderFilter;
import ua.newerp.application.orders.OrderListItem;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderStatusDto;

/** Same routes as OrderEndpoints in .NET. A @RestController returns objects; Spring writes them as JSON. */
@RestController
@RequestMapping("/orders")
@Tag(name = "Замовлення")
class OrderController {

    private final OrderQueries queries;

    OrderController(OrderQueries queries) {
        this.queries = queries;
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
}
