package ua.newerp.api.competitors;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.competitors.AddCompetitorLinkRequest;
import ua.newerp.application.competitors.CompetitorCheckResult;
import ua.newerp.application.competitors.CompetitorDto;
import ua.newerp.application.competitors.CompetitorLinkDto;
import ua.newerp.application.competitors.CompetitorPriceDto;
import ua.newerp.application.competitors.CompetitorPriceRequest;
import ua.newerp.application.competitors.CompetitorReportDto;
import ua.newerp.application.competitors.CompetitorService;
import ua.newerp.application.competitors.SaveCompetitorRequest;

/** Competitor price monitoring (Admin and Manager). */
@RestController
@RequestMapping("/competitors")
@Tag(name = "Ціни конкурентів")
class CompetitorController {

    private final CompetitorService service;

    CompetitorController(CompetitorService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Конкуренти з кількістю сторінок товарів")
    List<CompetitorDto> competitors() {
        return service.competitors();
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Новий конкурент: назва, сайт, необов'язковий вираз для ціни (перша група — ціна)")
    ResponseEntity<CompetitorDto> create(@RequestBody SaveCompetitorRequest request) {
        CompetitorDto competitor = service.createCompetitor(request);
        return ResponseEntity.created(URI.create("/competitors/" + competitor.id())).body(competitor);
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Змінити конкурента; active = false — не перевіряти і не показувати у звіті")
    CompetitorDto update(@PathVariable int id, @RequestBody SaveCompetitorRequest request) {
        return service.updateCompetitor(id, request);
    }

    @GetMapping("/links")
    @Operation(summary = "Сторінки товарів у конкурентів з останньою ціною; productId — лише одного товару")
    List<CompetitorLinkDto> links(@RequestParam(required = false) Integer productId) {
        return service.links(productId);
    }

    @PostMapping("/links")
    @Operation(summary = "Додати сторінку товару в конкурента (адреса має бути на сайті конкурента)")
    ResponseEntity<CompetitorLinkDto> addLink(@RequestBody AddCompetitorLinkRequest request) {
        CompetitorLinkDto link = service.addLink(request);
        return ResponseEntity.created(URI.create("/competitors/links/" + link.id())).body(link);
    }

    @DeleteMapping("/links/{id:\\d+}")
    @Operation(summary = "Видалити сторінку разом з історією цін")
    ResponseEntity<Void> deleteLink(@PathVariable int id) {
        service.deleteLink(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/links/{id:\\d+}/price")
    @Operation(summary = "Уписати ціну вручну (коли сайт не дає її прочитати)")
    CompetitorLinkDto setPrice(@PathVariable int id, @RequestBody CompetitorPriceRequest request) {
        return service.setPrice(id, request);
    }

    @PostMapping("/links/{id:\\d+}/check")
    @Operation(summary = "Перевірити ціну на сторінці зараз")
    CompetitorLinkDto check(@PathVariable int id) {
        return service.check(id);
    }

    @GetMapping("/links/{id:\\d+}/history")
    @Operation(summary = "Історія цін сторінки, новіші першими (рядок — коли ціна змінилась)")
    List<CompetitorPriceDto> history(@PathVariable int id) {
        return service.history(id);
    }

    @PostMapping("/check")
    @Operation(summary = "Перевірити ціни всіх сторінок активних конкурентів (те саме робить щоденна задача)")
    CompetitorCheckResult checkAll() {
        return service.checkAll();
    }

    @GetMapping("/report")
    @Operation(summary = "Наша ціна проти цін конкурентів: найнижча ціна і різниця у відсотках")
    CompetitorReportDto report() {
        return service.report();
    }
}
