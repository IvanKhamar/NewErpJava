package ua.newerp.domain.sms;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import ua.newerp.domain.common.DomainException;

/** An SMS sent (or tried) to a customer, a row of dd_sms_log of the old site. */
public class SmsMessage {

    /** A sane limit: longer is several SMS already, and the manager most likely made a mistake. */
    public static final int MAX_LENGTH = 670;

    /** The pre_order_id value the old site marks birthday greeting SMS with. */
    public static final int BIRTHDAY_MARKER = 1;

    private static final Pattern NON_DIGITS = Pattern.compile("\\D");
    private static final Pattern TTN_NUMBER = Pattern.compile("(?<!\\d)\\d{14}(?!\\d)");

    private Integer id;
    private int orderId;
    private int preOrderId;
    private String phone = "";
    private String text = "";
    private String response = "";
    private LocalDateTime sentAt;

    protected SmsMessage() {
    }

    public SmsMessage(int orderId, String phone, String text, String response, LocalDateTime sentAt) {
        this.orderId = orderId;
        this.phone = phone;
        this.text = text;
        this.response = response;
        this.sentAt = sentAt;
    }

    /** A birthday greeting: without an order, marked with BIRTHDAY_MARKER. */
    public static SmsMessage birthday(String phone, String text, String response, LocalDateTime sentAt) {
        var message = new SmsMessage(0, phone, text, response, sentAt);
        message.preOrderId = BIRTHDAY_MARKER;
        return message;
    }

    public static String requireText(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            throw new DomainException("Текст SMS порожній.");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new DomainException("Текст SMS довший за " + MAX_LENGTH + " символів.");
        }
        return trimmed;
    }

    /**
     * Brings a Ukrainian number to the 380XXXXXXXXX form the SMS service expects:
     * "+38 (067) 123-45-67", "0671234567" and "380671234567" give the same result.
     */
    public static String normalizePhone(String phone) {
        String digits = NON_DIGITS.matcher(phone == null ? "" : phone).replaceAll("");
        if (digits.length() == 10 && digits.startsWith("0")) {
            digits = "38" + digits;
        }
        if (digits.length() != 12 || !digits.startsWith("380")) {
            throw new DomainException("Номер \"" + phone + "\" не схожий на український мобільний.");
        }
        return digits;
    }

    /** Nova Poshta waybill numbers in a text: exactly 14 digits in a row (not part of a longer number). */
    public static List<String> findTtnNumbers(String text) {
        return TTN_NUMBER.matcher(text).results().map(m -> m.group()).distinct().toList();
    }

    public Integer getId() {
        return id;
    }

    public int getOrderId() {
        return orderId;
    }

    /** Despite the name, the old site writes 1 here for birthday greetings (BIRTHDAY_MARKER), otherwise 0. */
    public int getPreOrderId() {
        return preOrderId;
    }

    public String getPhone() {
        return phone;
    }

    public String getText() {
        return text;
    }

    /** The SMS service's response as is (for the stub: a note that no SMS was sent). */
    public String getResponse() {
        return response;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
