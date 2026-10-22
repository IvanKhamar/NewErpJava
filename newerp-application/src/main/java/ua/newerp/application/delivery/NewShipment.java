package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import ua.newerp.domain.delivery.ShipmentSeat;

/** Everything the Nova Poshta API needs for a new waybill (already checked, with a normalized phone). */
public record NewShipment(
        String senderId,
        String dropoffPointId,
        int orderId,
        LocalDate shipDate,
        String firstName,
        String lastName,
        String middleName,
        String phone,
        String cityRef,
        String warehouseRef,
        BigDecimal declaredCost,
        BigDecimal cashOnDelivery,
        boolean senderPays,
        List<ShipmentSeat> seats) {
}
