package ua.newerp.application.delivery;

/** PDF of a waybill or markings. */
public record PrintedShipment(byte[] pdf, String fileName) {
}
