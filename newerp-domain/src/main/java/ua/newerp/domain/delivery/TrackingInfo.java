package ua.newerp.domain.delivery;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** What Nova Poshta tracking reported about one waybill (TTN). */
public record TrackingInfo(
        String number,
        String status,
        int statusCode,
        String recipientAddress,
        boolean redelivery,
        LocalDateTime dateCreated,
        LocalDate dateScheduled,
        LocalDate datePayedKeeping) {
}
