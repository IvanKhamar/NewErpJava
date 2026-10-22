package ua.newerp.application.sms;

/** A waybill number in the SMS text belongs to another order: most likely the manager pasted the wrong one. */
public record TtnMismatchDto(String ttn, int ttnOrderId) {
}
