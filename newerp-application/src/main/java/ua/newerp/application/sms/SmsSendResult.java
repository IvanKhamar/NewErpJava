package ua.newerp.application.sms;

/** Result from the SMS service. response is the raw reply text; it goes to the log. */
public record SmsSendResult(boolean sent, String response) {
}
