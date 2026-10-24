package ua.newerp.api.reports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ua.newerp.application.reports.ReportService;
import ua.newerp.application.reports.RevenueFeedDto;
import ua.newerp.application.reports.RevenueFeedProperties;

/**
 * Revenue for an external dashboard, as the old site's revenue data. No login: the dashboard sends the feed token
 * (revenue-feed.token) instead. Without a token in the settings the feed does not exist (404).
 */
@RestController
@Tag(name = "Звіти")
class RevenueFeedController {

    private final ReportService service;
    private final RevenueFeedProperties options;

    RevenueFeedController(ReportService service, RevenueFeedProperties options) {
        this.service = service;
        this.options = options;
    }

    @GetMapping("/feeds/revenue.json")
    @Operation(summary = "Виручка для зовнішнього дашборда: сьогодні, вчора, місяць проти тих самих днів минулого, рік і ряд за days днів. "
            + "Токен — у заголовку X-Feed-Token або ?token=")
    ResponseEntity<RevenueFeedDto> revenue(@RequestHeader(name = "X-Feed-Token", required = false) String header,
            @RequestParam(required = false) String token, @RequestParam(defaultValue = "30") int days) {
        if (!options.enabled()) {
            return ResponseEntity.notFound().build();
        }
        if (!matches(header != null ? header : token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(service.revenueFeed(days, options.maxDays()));
    }

    /** Compared in constant time, so the answer time does not tell how much of a guess was right. */
    private boolean matches(String given) {
        return given != null && MessageDigest.isEqual(given.strip().getBytes(StandardCharsets.UTF_8),
                options.token().getBytes(StandardCharsets.UTF_8));
    }
}
