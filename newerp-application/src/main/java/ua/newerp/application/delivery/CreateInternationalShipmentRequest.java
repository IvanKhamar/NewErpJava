package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.delivery.InternationalShipment;
import ua.newerp.domain.delivery.ShipmentSeat;

/**
 * The international waybill form. The parts are the domain's plain records; InternationalShipment.of checks them all at once.
 *
 * @param parcels sizes in cm, weight in kg (the weight counts only when there are several parcels)
 */
public record CreateInternationalShipmentRequest(InternationalShipment.Recipient recipient, InternationalShipment.Terms terms,
        List<InternationalShipment.Item> items, List<ShipmentSeat> parcels, BigDecimal declaredCost, String currency) {
}
