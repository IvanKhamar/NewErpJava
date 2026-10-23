package ua.newerp.api.production;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import ua.newerp.application.common.CreatedId;
import ua.newerp.application.production.CuttingPlanDto;
import ua.newerp.application.production.CuttingPlanService;
import ua.newerp.application.production.ProductionLogDto;
import ua.newerp.application.production.ProductionLogFilter;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionModelFilter;
import ua.newerp.application.production.ProductionModelSummaryDto;
import ua.newerp.application.production.ProductionOperation;
import ua.newerp.application.production.ProductionOperationRequest;
import ua.newerp.application.production.ProductionOperationResult;
import ua.newerp.application.production.ProductionRepository;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.SewingPlanDto;
import ua.newerp.application.production.SewingPlanService;
import ua.newerp.application.production.StorefrontSyncResult;
import ua.newerp.application.production.SaveComponentRequest;
import ua.newerp.application.production.SaveProductionModelRequest;
import ua.newerp.application.production.SetComponentBomRequest;
import ua.newerp.application.production.SetProductionLinksRequest;
import ua.newerp.application.production.SetSewnBomRequest;

/** Admin and Storekeeper (see SecurityConfig). Same routes as ProductionEndpoints in .NET. */
@RestController
@RequestMapping("/production")
@Tag(name = "Виробництво")
class ProductionController {

    private final ProductionRepository repository;
    private final ProductionService service;
    private final SewingPlanService plan;
    private final CuttingPlanService cuttingPlan;

    ProductionController(ProductionRepository repository, ProductionService service, SewingPlanService plan,
            CuttingPlanService cuttingPlan) {
        this.repository = repository;
        this.service = service;
        this.plan = plan;
        this.cuttingPlan = cuttingPlan;
    }

    @GetMapping("/models")
    @Operation(summary = "Моделі з підсумками крою й пошиву; belowMinimumOnly=true — ті, яких пошито менше за minTotal")
    List<ProductionModelSummaryDto> models(ProductionModelFilter filter) {
        return repository.list(filter);
    }

    @GetMapping("/models/{id:\\d+}")
    @Operation(summary = "Модель: розміри із залишками, прив'язки до товарів, деталі крою, специфікації")
    ResponseEntity<ProductionModelDto> model(@PathVariable int id) {
        return ResponseEntity.of(repository.getDto(id));
    }

    @PostMapping("/models")
    ResponseEntity<CreatedId> createModel(@RequestBody SaveProductionModelRequest request) {
        return created("/production/models/", service.createModel(request));
    }

    @PutMapping("/models/{id:\\d+}")
    @Operation(summary = "Змінити модель і перелік розмірів (розміри зіставляються за назвою, залишки зберігаються)")
    ResponseEntity<Void> updateModel(@PathVariable int id, @RequestBody SaveProductionModelRequest request) {
        service.updateModel(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/models/{id:\\d+}")
    @Operation(summary = "Приховати модель (status = 0, як у старому модулі)")
    ResponseEntity<Void> deactivateModel(@PathVariable int id) {
        service.deactivateModel(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/models/{id:\\d+}/links")
    @Operation(summary = "Прив'язати модель до товарів OpenCart (напряму або через категорію з фільтром) і розміри до опцій")
    ResponseEntity<Void> setLinks(@PathVariable int id, @RequestBody SetProductionLinksRequest request) {
        service.setLinks(id, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/models/{id:\\d+}/sewn-bom")
    @Operation(summary = "Специфікація пошиву: деталі крою й розхідники на один виріб")
    ResponseEntity<Void> setSewnBom(@PathVariable int id, @RequestBody SetSewnBomRequest request) {
        service.setSewnBom(id, request);
        return ResponseEntity.noContent().build();
    }

    // Four stock operations. Each takes quantities for several sizes at once.

    @GetMapping("/plan")
    @Operation(summary = "План пошиву: скільки кожного розміру пошити й розкроїти для активних замовлень (production.plan-status-ids); "
            + "onlyToSew=false — також розміри, яких вистачає")
    SewingPlanDto plan(@RequestParam(required = false) Boolean onlyToSew) {
        return plan.get(onlyToSew == null || onlyToSew);
    }

    @GetMapping("/cut-plan")
    @Operation(summary = "План крою: скільки кожного розміру розкроїти до мінімального запасу моделі (minTotal за частками розмірів) "
            + "плюс те, чого бракує активним замовленням, і чи вистачить розхідників; onlyToCut=false — також моделі, яких вистачає")
    CuttingPlanDto cutPlan(@RequestParam(required = false) Boolean onlyToCut) {
        return cuttingPlan.get(onlyToCut == null || onlyToCut);
    }

    @PostMapping("/storefront/sync")
    @Operation(summary = "Передати пошиті залишки всіх активних моделей на вітрину магазину (кількість розмірів і товарів у OpenCart)")
    StorefrontSyncResult syncStorefront() {
        return service.pushStorefront();
    }

    @PostMapping("/models/{id:\\d+}/cut")
    @Operation(summary = "+Покроєно: крій зростає, розхідники списуються за специфікацією крою (бракує — списується те, що є, у warnings)")
    ProductionOperationResult cut(@PathVariable int id, @RequestBody ProductionOperationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return record(id, ProductionOperation.CUT, request, user);
    }

    @PostMapping("/models/{id:\\d+}/to-sewing")
    @Operation(summary = "В пошив: розкроєні комплекти передано швачкам (більше, ніж розкроєно, не можна)")
    ProductionOperationResult toSewing(@PathVariable int id, @RequestBody ProductionOperationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return record(id, ProductionOperation.SEND_TO_SEWING, request, user);
    }

    @PostMapping("/models/{id:\\d+}/sewn")
    @Operation(summary = "+Пошито: пошиті вироби зростають, деталі й розхідники списуються за специфікацією пошиву")
    ProductionOperationResult sewn(@PathVariable int id, @RequestBody ProductionOperationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return record(id, ProductionOperation.SEWN, request, user);
    }

    @PostMapping("/models/{id:\\d+}/ship")
    @Operation(summary = "Відвантажено не через замовлення (більше, ніж пошито, не можна)")
    ProductionOperationResult ship(@PathVariable int id, @RequestBody ProductionOperationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return record(id, ProductionOperation.SHIP, request, user);
    }

    @GetMapping("/log")
    @Operation(summary = "Журнал крою й пошиву (stage = cut або sewn), нові записи першими")
    List<ProductionLogDto> log(ProductionLogFilter filter) {
        return repository.listLog(filter);
    }

    @PostMapping("/models/{id:\\d+}/components")
    @Operation(summary = "Додати деталь крою (перед, спинка, рукав...)")
    ResponseEntity<CreatedId> addComponent(@PathVariable int id, @RequestBody SaveComponentRequest request) {
        return created("/production/components/", service.addComponent(id, request));
    }

    @PutMapping("/components/{id:\\d+}")
    ResponseEntity<Void> updateComponent(@PathVariable int id, @RequestBody SaveComponentRequest request) {
        service.updateComponent(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/components/{id:\\d+}")
    ResponseEntity<Void> deleteComponent(@PathVariable int id) {
        service.deleteComponent(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/components/{id:\\d+}/bom")
    @Operation(summary = "Специфікація крою: розхідники на одну деталь")
    ResponseEntity<Void> setComponentBom(@PathVariable int id, @RequestBody SetComponentBomRequest request) {
        service.setComponentBom(id, request);
        return ResponseEntity.noContent().build();
    }

    private ProductionOperationResult record(int id, ProductionOperation operation, ProductionOperationRequest request,
            AuthenticatedUser user) {
        return service.record(id, operation, request, user.user().userId(), user.user().username());
    }

    private static ResponseEntity<CreatedId> created(String path, int id) {
        return ResponseEntity.created(URI.create(path + id)).body(new CreatedId(id));
    }
}
