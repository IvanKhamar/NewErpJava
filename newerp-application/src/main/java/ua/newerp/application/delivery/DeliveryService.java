package ua.newerp.application.delivery;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.delivery.ShipmentDocument;
import ua.newerp.domain.delivery.TrackingInfo;

/** Nova Poshta delivery: waybill tracking and reminders to pick up a parcel (based on the old site's module). */
@Service
public class DeliveryService {

    /** The Nova Poshta API takes up to 100 waybills in one tracking request. */
    public static final int TRACKING_BATCH_SIZE = 100;

    /** How many days before paid storage to remind (the old site: exactly 3 days). */
    public static final int REMIND_DAYS_BEFORE = 3;

    private final ShipmentRepository repository;
    private final NovaPoshtaClient client;
    private final Clock clock;

    public DeliveryService(ShipmentRepository repository, NovaPoshtaClient client, Clock clock) {
        this.repository = repository;
        this.client = client;
        this.clock = clock;
    }

    /**
     * Refreshes the statuses of all open waybills. The API is called outside a database transaction;
     * the changed waybills are saved together at the end.
     */
    public TrackingRefreshResult refreshTracking() {
        if (!client.isConfigured()) {
            return new TrackingRefreshResult(false, 0, 0);
        }

        List<OpenShipment> open = repository.listOpenForTracking();
        Map<String, ShipmentDocument> byNumber = new LinkedHashMap<>();
        open.forEach(s -> byNumber.putIfAbsent(s.document().getNumber(), s.document()));
        var changed = new ArrayList<ShipmentDocument>();

        for (int from = 0; from < open.size(); from += TRACKING_BATCH_SIZE) {
            List<TrackingRequest> batch = open.subList(from, Math.min(from + TRACKING_BATCH_SIZE, open.size())).stream()
                    .map(s -> new TrackingRequest(s.document().getNumber(), s.phone()))
                    .toList();
            for (TrackingInfo info : client.track(batch)) {
                ShipmentDocument document = byNumber.get(info.number());
                if (document != null && document.applyTracking(info)) {
                    changed.add(document);
                }
            }
        }

        repository.saveTracking(changed);
        return new TrackingRefreshResult(true, open.size(), changed.size());
    }

    /**
     * Whom to remind to pick up a parcel: paid storage starts from today up to three days ahead.
     * The old site showed only those with exactly 3 days left: if a day was missed, the person fell off the list.
     */
    public List<ReminderDto> reminders() {
        LocalDate today = LocalDate.now(clock);
        return repository.listReminders(today, today.plusDays(REMIND_DAYS_BEFORE));
    }

    public void markReminded(int orderId) {
        if (!repository.markReminded(orderId, LocalDateTime.now(clock))) {
            throw new NotFoundException("Замовлення " + orderId + " не знайдено.");
        }
    }
}
