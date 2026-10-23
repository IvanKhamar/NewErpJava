package ua.newerp.api.cashbox;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ua.newerp.application.cashbox.AutoCloseResult;
import ua.newerp.application.cashbox.CashboxService;
import ua.newerp.application.cashbox.CheckboxClient;

/**
 * A background job instead of the old site's cron (cashbox/work_shift/cron): every few minutes it checks open shifts against
 * Checkbox and closes those whose cashier's auto-close time has come. Does nothing with the Checkbox stub.
 * {@code @Scheduled} is Spring's version of a BackgroundService with a PeriodicTimer; scheduling is switched on by
 * {@code @EnableScheduling} in NewErpApplication.
 */
@Component
@ConditionalOnExpression("${checkbox.auto-close-interval-minutes:15} > 0")
class CashboxAutoCloseJob {

    private static final Logger log = LoggerFactory.getLogger(CashboxAutoCloseJob.class);

    private final CashboxService service;
    private final CheckboxClient client;

    CashboxAutoCloseJob(CashboxService service, CheckboxClient client) {
        this.service = service;
        this.client = client;
    }

    @Scheduled(initialDelayString = "${checkbox.auto-close-interval-minutes:15}",
            fixedDelayString = "${checkbox.auto-close-interval-minutes:15}", timeUnit = TimeUnit.MINUTES)
    void run() {
        if (client.isFake()) {
            return;
        }
        try {
            AutoCloseResult result = service.autoClose();
            if (result.closedByCheckbox() + result.closedBySchedule() > 0 || !result.errors().isEmpty()) {
                log.info("Автозакриття змін: {}", result);
            }
        } catch (RuntimeException e) {
            log.error("Автозакриття змін не вдалося", e);
        }
    }
}
