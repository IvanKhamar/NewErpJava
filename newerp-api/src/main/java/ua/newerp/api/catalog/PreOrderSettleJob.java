package ua.newerp.api.catalog;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ua.newerp.application.catalog.PreOrderService;
import ua.newerp.application.catalog.PreOrderSettleResult;

/**
 * A background job instead of the old site's cron (tool/console updatePreOrdersQuantity): regularly takes what was sold ahead
 * off the pre-order quotas. Built like CashboxAutoCloseJob; 0 minutes switches it off.
 */
@Component
@ConditionalOnExpression("${pre-orders.settle-interval-minutes:60} > 0")
class PreOrderSettleJob {

    private static final Logger log = LoggerFactory.getLogger(PreOrderSettleJob.class);

    private final PreOrderService service;

    PreOrderSettleJob(PreOrderService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "${pre-orders.settle-interval-minutes:60}",
            fixedDelayString = "${pre-orders.settle-interval-minutes:60}", timeUnit = TimeUnit.MINUTES)
    void run() {
        try {
            PreOrderSettleResult result = service.settle();
            if (result.sizes() > 0) {
                log.info("Передзамовлення: списано з лімітів у {} розмірах", result.sizes());
            }
        } catch (RuntimeException e) {
            log.error("Списати передзамовлення не вдалося", e);
        }
    }
}
