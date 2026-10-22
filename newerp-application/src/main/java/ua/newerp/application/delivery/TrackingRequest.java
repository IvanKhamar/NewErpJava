package ua.newerp.application.delivery;

/** What tracking needs: the waybill number and the recipient's phone (with it the API returns full data). */
public record TrackingRequest(String number, String phone) {
}
