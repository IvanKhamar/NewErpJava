package ua.newerp.application.sms;

/**
 * sent: whether the SMS really went to the customer (the stub always says false).
 * If ttnMismatch is not null, nothing was sent or logged: confirm (force) or fix the text.
 */
public record SendSmsResultDto(boolean sent, SmsDto message, TtnMismatchDto ttnMismatch) {
}
