package ua.newerp.application.delivery;

import ua.newerp.domain.delivery.ShipmentDocument;

/** A waybill that can still change, with the order's phone. */
public record OpenShipment(ShipmentDocument document, String phone) {
}
