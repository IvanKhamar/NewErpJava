package ua.newerp.api.catalog;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.catalog.CrossSellItemDto;
import ua.newerp.application.catalog.CrossSellService;
import ua.newerp.application.catalog.SaveCrossSellItemRequest;
import ua.newerp.application.common.LookupItem;

/** The checkout cross-sell panel: which products and categories it offers and with what discount (Admin and Manager). */
@RestController
@RequestMapping("/catalog/crosssell")
@Tag(name = "Порядок товарів")
class CrossSellController {

    private final CrossSellService service;

    CrossSellController(CrossSellService service) {
        this.service = service;
    }

    @GetMapping("/items")
    @Operation(summary = "Рядки панелі допродажу: спершу категорії, потім товари, з назвами й кількістю увімкнених товарів")
    List<CrossSellItemDto> items() {
        return service.list();
    }

    @PostMapping("/items")
    @Operation(summary = "Додати товар (type product) або категорію (type category) зі знижкою у відсотках")
    ResponseEntity<CrossSellItemDto> add(@RequestBody SaveCrossSellItemRequest request) {
        CrossSellItemDto item = service.add(request);
        return ResponseEntity.created(URI.create("/catalog/crosssell/items/" + item.id())).body(item);
    }

    @PutMapping("/items/{id:\\d+}/discount")
    @Operation(summary = "Змінити знижку рядка, %")
    CrossSellItemDto setDiscount(@PathVariable int id, @RequestBody DiscountRequest request) {
        return service.setDiscount(id, request.discount());
    }

    @DeleteMapping("/items/{id:\\d+}")
    @Operation(summary = "Прибрати рядок з панелі")
    ResponseEntity<Void> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    @Operation(summary = "Увімкнені категорії для вибору")
    List<LookupItem> categories() {
        return service.categories();
    }

    record DiscountRequest(BigDecimal discount) {
    }
}
