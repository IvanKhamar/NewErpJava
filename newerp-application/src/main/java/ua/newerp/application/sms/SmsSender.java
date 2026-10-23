package ua.newerp.application.sms;

/**
 * Sending SMS through an external service. The real sender (SMSClub) works only with a token in application-local.yml
 * or an environment variable; without it a stub that sends nothing is registered.
 */
public interface SmsSender {

    /** Whether the real service is connected. True by default; the stub returns false. */
    default boolean isConfigured() {
        return true;
    }

    /** @param phone number in the 380XXXXXXXXX form */
    SmsSendResult send(String phone, String text);
}
