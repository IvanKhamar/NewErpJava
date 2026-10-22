package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.delivery.ShipmentSeat;

/** senderId / dropoffPointId = null: the defaults from the settings. cashOnDelivery = 0: no cash on delivery. */
public record CreateShipmentRequest(
        String firstName,
        String lastName,
        String middleName,
        String phone,
        String cityRef,
        String warehouseRef,
        BigDecimal declaredCost,
        BigDecimal cashOnDelivery,
        boolean senderPays,
        List<ShipmentSeat> seats,
        String senderId,
        String dropoffPointId) {

    public CreateShipmentRequest {
        declaredCost = declaredCost == null ? BigDecimal.ZERO : declaredCost;
        cashOnDelivery = cashOnDelivery == null ? BigDecimal.ZERO : cashOnDelivery;
        seats = seats == null ? List.of() : seats;
    }
}
