package ua.newerp.api.orders;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.orders.CreateOrderRequest;
import ua.newerp.application.orders.CreateOrderResult;
import ua.newerp.application.orders.CustomerPickDto;
import ua.newerp.application.orders.CustomerRiskDto;
import ua.newerp.application.orders.CustomerRiskQuery;
import ua.newerp.application.orders.NewOrderService;
import ua.newerp.application.orders.OrderCompositionDto;
import ua.newerp.application.orders.OrderMethodsDto;
import ua.newerp.application.orders.ProductPickDto;
import ua.newerp.application.orders.UpdateOrderCompositionRequest;
import ua.newerp.application.orders.UpdateOrderCompositionResult;

/** Creating an order by hand, the customer risk check and changing an order's composition (part of OrderEndpoints in .NET). */
@RestController
@RequestMapping("/orders")
@Tag(name = "Замовлення")
class NewOrderController {

    private final NewOrderService service;

    NewOrderController(NewOrderService service) {
        this.service = service;
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Створити замовлення вручну (дзвінок, соцмережі). risk — чи скасовував клієнт попередні замовлення")
    ResponseEntity<CreateOrderResult> create(@RequestBody CreateOrderRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        CreateOrderResult result = service.create(request, user.user().userId());
        return ResponseEntity.created(URI.create("/orders/" + result.orderId())).body(result);
    }

    @GetMapping("/new/methods")
    @Operation(summary = "Способи доставки й оплати, які вже траплялись у замовленнях")
    OrderMethodsDto methods() {
        return service.listMethods();
    }

    @GetMapping("/new/products")
    @Operation(summary = "Пошук товару за назвою, моделлю чи id: ціна й розміри для нового замовлення")
    List<ProductPickDto> products(@RequestParam(required = false) String search) {
        return service.searchProducts(search);
    }

    @GetMapping("/new/customers")
    @Operation(summary = "Клієнти з попередніх замовлень (від 3 символів імені, телефону чи email)")
    List<CustomerPickDto> customers(@RequestParam(required = false) String search) {
        return service.searchCustomers(search);
    }

    @GetMapping("/customer-risk")
    @Operation(summary = "Чи скасовував клієнт замовлення за останні 4 роки (за телефоном, ім'ям і прізвищем або email)")
    CustomerRiskDto customerRisk(CustomerRiskQuery query) {
        return service.checkRisk(query);
    }

    @GetMapping("/{id:\\d+}/composition")
    @Operation(summary = "Склад замовлення для редагування: товари з розмірами, доставка, знижка, завдаток")
    OrderCompositionDto composition(@PathVariable int id) {
        return service.getComposition(id);
    }

    @PutMapping("/{id:\\d+}/composition")
    @Operation(summary = "Замінити склад замовлення (товари, розміри, кількість, ціни, доставка, знижка, завдаток). "
            + "Пошиті вироби перераховуються за новим складом; warnings — нестача")
    UpdateOrderCompositionResult updateComposition(@PathVariable int id, @RequestBody UpdateOrderCompositionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.updateComposition(id, request, user.user().userId());
    }
}
