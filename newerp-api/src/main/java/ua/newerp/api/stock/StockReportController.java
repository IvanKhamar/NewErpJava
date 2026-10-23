package ua.newerp.api.stock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.stock.StockReportDto;
import ua.newerp.application.stock.StockReportService;

/** Stock reports (Admin and Storekeeper, like the rest of /stock). */
@RestController
@Tag(name = "Склад: розхідники")
class StockReportController {

    private final StockReportService service;

    StockReportController(StockReportService service) {
        this.service = service;
    }

    @GetMapping("/stock/reports")
    @Operation(summary = "Звіти складу за останні days днів: вартість залишків за категоріями, витрата кожного розхідника "
            + "з тим, на скільки днів вистачить і що замовити на coverDays днів, і випуск виробництва за моделями й розмірами; "
            + "інвентаризація показана окремо від витрати")
    StockReportDto get(@RequestParam(defaultValue = "30") int days, @RequestParam(defaultValue = "30") int coverDays) {
        return service.get(days, coverDays);
    }
}
