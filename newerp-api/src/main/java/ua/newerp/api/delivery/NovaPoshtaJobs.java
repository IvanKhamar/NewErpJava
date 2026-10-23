package ua.newerp.api.delivery;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ua.newerp.application.delivery.DeliveryService;
import ua.newerp.application.delivery.NovaPoshtaClient;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.delivery.TrackingRefreshResult;
import ua.newerp.application.delivery.WarehouseRefreshResult;

/**
 * The old site's delivery crons as background jobs: waybill statuses every few minutes, the branch directory once a night.
 * Both do nothing without an API key (the stub client), so local development never calls Nova Poshta.
 */
@Component
class NovaPoshtaJobs {

    private static final Logger log = LoggerFactory.getLogger(NovaPoshtaJobs.class);

    private final DeliveryService delivery;
    private final ShipmentService shipments;
    private final NovaPoshtaClient client;

    NovaPoshtaJobs(DeliveryService delivery, ShipmentService shipments, NovaPoshtaClient client) {
        this.delivery = delivery;
        this.shipments = shipments;
        this.client = client;
    }

    /** Statuses of the open waybills (the same as POST /delivery/tracking/refresh). */
    @Component
    @ConditionalOnExpression("${nova-poshta.tracking-interval-minutes:30} > 0")
    static class Tracking {

        private final NovaPoshtaJobs jobs;

        Tracking(NovaPoshtaJobs jobs) {
            this.jobs = jobs;
        }

        @Scheduled(initialDelayString = "${nova-poshta.tracking-interval-minutes:30}",
                fixedDelayString = "${nova-poshta.tracking-interval-minutes:30}", timeUnit = TimeUnit.MINUTES)
        void run() {
            jobs.refreshTracking();
        }
    }

    /** The branch and parcel locker directory, at night when nobody is creating waybills. */
    @Component
    @ConditionalOnExpression("'${nova-poshta.warehouses-cron:0 30 4 * * *}' != '-'")
    static class Warehouses {

        private final NovaPoshtaJobs jobs;

        Warehouses(NovaPoshtaJobs jobs) {
            this.jobs = jobs;
        }

        @Scheduled(cron = "${nova-poshta.warehouses-cron:0 30 4 * * *}", zone = "Europe/Kyiv")
        void run() {
            jobs.refreshWarehouses();
        }
    }

    void refreshTracking() {
        if (!client.isConfigured()) {
            return;
        }
        try {
            TrackingRefreshResult result = delivery.refreshTracking();
            if (result.updated() > 0) {
                log.info("Статуси накладних: перевірено {}, змінилося {}", result.checked(), result.updated());
            }
        } catch (RuntimeException e) {
            log.error("Оновлення статусів накладних не вдалося", e);
        }
    }

    void refreshWarehouses() {
        if (!client.isConfigured()) {
            return;
        }
        try {
            WarehouseRefreshResult result = shipments.refreshWarehouses();
            log.info("Довідник відділень Нової Пошти оновлено: {}", result.count());
        } catch (RuntimeException e) {
            log.error("Оновлення довідника відділень не вдалося", e);
        }
    }
}
