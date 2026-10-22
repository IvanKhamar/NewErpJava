package ua.newerp.application.sms;

/**
 * Sending SMS through an external service. Only a stub that sends nothing is registered now;
 * the real sender (SMSClub) comes with a new token.
 */
public interface SmsSender {

    /** @param phone number in the 380XXXXXXXXX form */
    SmsSendResult send(String phone, String text);
}
