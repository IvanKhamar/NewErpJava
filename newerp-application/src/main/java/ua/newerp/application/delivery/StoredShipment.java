package ua.newerp.application.delivery;

import ua.newerp.domain.delivery.ShipmentDocument;

/** A waybill with the id of the sender it was created from (null: created on the old site). */
public record StoredShipment(ShipmentDocument document, String senderId) {
}
