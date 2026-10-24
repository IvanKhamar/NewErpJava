package ua.newerp.api.competitors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ua.newerp.application.competitors.CompetitorCheckResult;
import ua.newerp.application.competitors.CompetitorService;

/** Reads the competitors' prices once a day, in the morning before the managers start ("-" turns it off). */
@Component
@ConditionalOnExpression("'${competitors.check-cron:0 0 7 * * *}' != '-'")
class CompetitorPriceJob {

    private static final Logger log = LoggerFactory.getLogger(CompetitorPriceJob.class);

    private final CompetitorService service;

    CompetitorPriceJob(CompetitorService service) {
        this.service = service;
    }

    @Scheduled(cron = "${competitors.check-cron:0 0 7 * * *}", zone = "Europe/Kyiv")
    void run() {
        try {
            CompetitorCheckResult result = service.checkAll();
            if (result.checked() > 0) {
                log.info("Ціни конкурентів: перевірено {}, змінилося {}, не вдалося {}", result.checked(), result.changed(), result.failed());
            }
        } catch (RuntimeException e) {
            log.error("Перевірка цін конкурентів не вдалася", e);
        }
    }
}
