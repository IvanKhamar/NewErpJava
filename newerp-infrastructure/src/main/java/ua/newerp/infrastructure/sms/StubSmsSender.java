package ua.newerp.infrastructure.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ua.newerp.application.sms.SmsSendResult;
import ua.newerp.application.sms.SmsSender;

/**
 * Stub: sends nothing, only writes to the application log. This way the whole flow can be checked on a local
 * database, where phones are invented and may belong to real people.
 */
class StubSmsSender implements SmsSender {

    static final String RESPONSE = "НЕ НАДІСЛАНО: заглушка NewErp, справжній SMS-сервіс не підключено";

    // SLF4J is the logging facade of Java (like ILogger); {} are placeholders, filled only if the level is on
    private static final Logger LOG = LoggerFactory.getLogger(StubSmsSender.class);

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public SmsSendResult send(String phone, String text) {
        LOG.info("SMS-заглушка: на {} не надіслано повідомлення довжиною {}", phone, text.length());
        return new SmsSendResult(false, RESPONSE);
    }
}
