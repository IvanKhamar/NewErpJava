package ua.newerp.api.reports;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.reports.DashboardDto;
import ua.newerp.application.reports.ProductSalesDto;
import ua.newerp.application.reports.ReportFilter;
import ua.newerp.application.reports.ReportService;
import ua.newerp.application.reports.SalesReportDto;
import ua.newerp.application.reports.StatusReportDto;

/** Sales reports and the home page summary (Admin and Manager, see SecurityConfig). */
@RestController
@RequestMapping("/reports")
@Tag(name = "Звіти")
class ReportController {

    private final ReportService service;

    ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Зведення для головної: замовлення й продажі сьогодні та за місяць проти попередніх, середній чек, нові покупці, "
            + "графік за 30 днів, топ-5 товарів")
    DashboardDto dashboard() {
        return service.dashboard();
    }

    @GetMapping("/sales")
    @Operation(summary = "Продажі за періодами (group: Day, Week, Month, Year); типово — поточний місяць без скасованих замовлень")
    SalesReportDto sales(ReportFilter filter) {
        return service.sales(filter);
    }

    @GetMapping("/products")
    @Operation(summary = "Продані товари за кількістю (до 200); типово — поточний місяць без скасованих замовлень")
    ProductSalesDto products(ReportFilter filter) {
        return service.products(filter);
    }

    @GetMapping("/statuses")
    @Operation(summary = "Замовлення (кількість і сума) та повернення за статусами, скасовані теж; типово — поточний місяць")
    StatusReportDto statuses(ReportFilter filter) {
        return service.statuses(filter);
    }
}
