package ua.newerp.api.staff;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.staff.BonusReport;
import ua.newerp.application.staff.BonusService;

/** /staff/bonuses is Admin only (see SecurityConfig). Same route as StaffEndpoints in .NET. */
@RestController
@RequestMapping("/staff")
@Tag(name = "Персонал")
class StaffController {

    private final BonusService bonuses;

    StaffController(BonusService bonuses) {
        this.bonuses = bonuses;
    }

    @GetMapping("/bonuses")
    @Operation(summary = "Бонуси менеджерів за період (дати включно)")
    BonusReport bonuses(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return bonuses.calculate(from, to);
    }
}
