package ua.newerp.api.customers;

import java.util.List;

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
import ua.newerp.application.customers.AddCustomerNoteRequest;
import ua.newerp.application.customers.CustomerDetails;
import ua.newerp.application.customers.CustomerFilter;
import ua.newerp.application.customers.CustomerGroupDto;
import ua.newerp.application.customers.CustomerListItem;
import ua.newerp.application.customers.CustomerService;

/** Registered customers (Admin and Manager, see SecurityConfig). */
@RestController
@RequestMapping("/customers")
@Tag(name = "Покупці")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Зареєстровані покупці посторінково, нові зверху; фільтри: search (ім'я, e-mail, телефон, id), groupId, status")
    PagedResult<CustomerListItem> list(CustomerFilter filter) {
        return service.list(filter);
    }

    @GetMapping("/groups")
    @Operation(summary = "Групи покупців")
    List<CustomerGroupDto> groups() {
        return service.groups();
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Картка покупця: адреси, замовлення (за покупцем і тим самим телефоном), підсумки, ризик і нотатки")
    CustomerDetails get(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping("/{id:\\d+}/notes")
    @Operation(summary = "Нотатка менеджера про покупця")
    ResponseEntity<Void> addNote(@PathVariable int id, @RequestBody AddCustomerNoteRequest request) {
        service.addNote(id, request);
        return ResponseEntity.noContent().build();
    }
}
