package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Waybill of an order. tone: how to colour the status, as a number like the .NET API sends it
 * (0 Info, 1 Success, 2 Warning, 3 Error; the order of {@link ua.newerp.domain.delivery.ShipmentTone}).
 */
public record ShipmentDto(
        int id,
        int orderId,
        String carrier,
        String number,
        BigDecimal cost,
        String status,
        int statusCode,
        int tone,
        boolean isFinal,
        String recipientAddress,
        boolean redelivery,
        LocalDateTime dateCreated,
        LocalDate dateScheduled,
        LocalDate datePayedKeeping) {
}
