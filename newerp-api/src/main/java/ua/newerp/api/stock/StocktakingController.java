package ua.newerp.api.stock;

import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.auth.AuthenticatedUser;
import ua.newerp.application.stock.ApplyStocktakingRequest;
import ua.newerp.application.stock.StocktakingPreviewDto;
import ua.newerp.application.stock.StocktakingResult;
import ua.newerp.application.stock.StocktakingService;

/** Stocktaking of consumables (Admin and Storekeeper, like the rest of /stock). */
@RestController
@RequestMapping("/stock/stocktaking")
@Tag(name = "Склад: розхідники")
class StocktakingController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final StocktakingService service;

    StocktakingController(StocktakingService service) {
        this.service = service;
    }

    @GetMapping("/sheet.xlsx")
    @Operation(summary = "Відомість для інвентаризації в Excel: ID, Назва, Категорія, Од., За обліком і порожня колонка «Фактично»; "
            + "з warehouseId — облікове на цьому складі, без нього — на основному")
    ResponseEntity<byte[]> sheet(@RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer warehouseId) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("inventaryzatsiia-" + LocalDate.now() + ".xlsx").build().toString())
                .body(service.sheet(categoryId, warehouseId));
    }

    // The file comes as the raw request body, as in the warehouses import
    @PostMapping(path = "/preview", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Прочитати заповнену відомість: порахована кількість, облікова й різниця по кожній позиції; нічого не записує")
    StocktakingPreviewDto preview(@RequestBody byte[] file, @RequestParam(required = false) Integer warehouseId) {
        return service.preview(file, warehouseId);
    }

    @PostMapping
    @Operation(summary = "Провести інвентаризацію: залишок кожної позиції на порахованому місці (warehouseId; без нього — основний "
            + "склад) стає порахованим, різниця йде в журнал руху з коментарем «Інвентаризація»; з помилкою нічого не записується")
    StocktakingResult apply(@RequestBody ApplyStocktakingRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.apply(request, user.user().userId(), user.user().username());
    }
}
