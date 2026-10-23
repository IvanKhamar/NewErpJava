package ua.newerp.api.partners;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import ua.newerp.application.partners.PartnerDto;
import ua.newerp.application.partners.PartnerListItemDto;
import ua.newerp.application.partners.PartnerProductPickDto;
import ua.newerp.application.partners.PartnerService;
import ua.newerp.application.partners.SavePartnerRequest;
import ua.newerp.application.partners.ShopCategoryDto;

/** Reseller partners (Admin and Manager) and their public XML feeds (see SecurityConfig). */
@RestController
@Tag(name = "Партнери")
class PartnerController {

    private static final MediaType XML_UTF8 = MediaType.parseMediaType("application/xml; charset=utf-8");

    private final PartnerService service;

    PartnerController(PartnerService service) {
        this.service = service;
    }

    @GetMapping({"/partners", "/partners/"})
    @Operation(summary = "Партнери-перепродавці з кількістю рядків відповідності")
    List<PartnerListItemDto> list() {
        return service.list();
    }

    @GetMapping("/partners/{id:\\d+}")
    @Operation(summary = "Партнер з відповідністю категорій і переліком окремих товарів")
    ResponseEntity<PartnerDto> get(@PathVariable int id) {
        return ResponseEntity.of(service.get(id));
    }

    @PostMapping({"/partners", "/partners/"})
    ResponseEntity<Map<String, Integer>> create(@RequestBody SavePartnerRequest request) {
        int id = service.create(request);
        return ResponseEntity.created(URI.create("/partners/" + id)).body(Map.of("id", id));
    }

    @PutMapping("/partners/{id:\\d+}")
    @Operation(summary = "Змінити партнера разом з відповідностями (рядки з тією самою категорією чи товаром і розміром зберігають свій id)")
    ResponseEntity<Void> update(@PathVariable int id, @RequestBody SavePartnerRequest request) {
        service.update(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/partners/{id:\\d+}")
    ResponseEntity<Void> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/partners/categories")
    @Operation(summary = "Категорії магазину з розмірами їхніх товарів")
    List<ShopCategoryDto> categories() {
        return service.listCategories();
    }

    @GetMapping("/partners/products")
    @Operation(summary = "Пошук товарів за назвою чи id, з цінами розмірів")
    List<PartnerProductPickDto> products(@RequestParam(required = false) String search) {
        return service.searchProducts(search);
    }

    @GetMapping("/feeds/{code}.xml")
    @Operation(summary = "XML-фід партнера (yml_catalog) з його категоріями й цінами")
    ResponseEntity<String> feed(@PathVariable String code) {
        return service.buildFeedXml(code)
                .map(xml -> ResponseEntity.ok().contentType(XML_UTF8).body(xml))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
