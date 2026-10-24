package ua.newerp.api.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.catalog.ProductCardDto;
import ua.newerp.application.catalog.ProductFilter;
import ua.newerp.application.catalog.ProductListItem;
import ua.newerp.application.catalog.ProductService;
import ua.newerp.application.catalog.UpdateProductRequest;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.PagedResult;

/** Shop products (Admin and Manager, see SecurityConfig). */
@RestController
@RequestMapping("/catalog/products")
@Tag(name = "Товари")
class ProductController {

    private final ProductService service;

    ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Товари магазину з фільтрами (пошук, категорія, мітка, увімкнені) і пагінацією")
    PagedResult<ProductListItem> list(ProductFilter filter) {
        return service.list(filter);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Картка товару: розміри із залишками (лише для читання), мітки й категорії")
    ProductCardDto get(@PathVariable int id) {
        return service.get(id);
    }

    @PatchMapping("/{id:\\d+}")
    @Operation(summary = "Змінити ціну, увімкнення, мітки або категорії (null — без змін); dateModified з картки обов'язковий, "
            + "якщо товар змінили після цього — 409")
    ProductCardDto update(@PathVariable int id, @RequestBody UpdateProductRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/labels")
    @Operation(summary = "Усі мітки товарів")
    List<LookupItem> labels() {
        return service.labels();
    }

    @GetMapping("/categories")
    @Operation(summary = "Увімкнені категорії")
    List<LookupItem> categories() {
        return service.categories();
    }
}
