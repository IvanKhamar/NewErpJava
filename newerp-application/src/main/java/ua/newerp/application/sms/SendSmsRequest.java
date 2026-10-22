package ua.newerp.application.sms;

/** force: send even if a waybill number in the text belongs to another order (a person confirmed it). */
public record SendSmsRequest(String text, boolean force) {
}
