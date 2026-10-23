package ua.newerp.domain.marketing;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.sms.SmsMessage;

/** Exporting customers' contacts for mailings, as marketing/export of the old site. */
public final class ContactExport {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s\"(),:;<>\\[\\]\\\\]+@[^@\\s\"(),:;<>\\[\\]\\\\]+\\.[^@\\s\"(),:;<>\\[\\]\\\\.]+$");

    private ContactExport() {
    }

    /** The number as 380XXXXXXXXX, or null when it is not a Ukrainian mobile. */
    public static String cleanPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            return SmsMessage.normalizePhone(phone);
        } catch (DomainException e) {
            return null;
        }
    }

    public static String cleanEmail(String email) {
        String trimmed = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return EMAIL.matcher(trimmed).matches() ? trimmed : null;
    }

    /**
     * Unique contacts by the chosen field, sorted; first and last name, city and the other field come from the newest order
     * with this contact (empty when there is none).
     */
    public static List<ContactRow> build(ContactField field, Collection<String> values, Collection<OrderContact> orders) {
        UnaryOperator<String> clean = field == ContactField.PHONE ? ContactExport::cleanPhone : ContactExport::cleanEmail;
        Map<String, OrderContact> latest = new HashMap<>();
        for (OrderContact order : orders) {
            String key = clean.apply(field == ContactField.PHONE ? order.phone() : order.email());
            if (key != null) {
                latest.merge(key, order, (known, next) -> next.dateAdded().isAfter(known.dateAdded()) ? next : known);
            }
        }

        return values.stream().map(clean).filter(Objects::nonNull).distinct().sorted()
                .map(value -> {
                    OrderContact order = latest.get(value);
                    String first = order == null ? "" : order.firstName().strip();
                    String last = order == null ? "" : order.lastName().strip();
                    String city = order == null ? "" : order.city().strip();
                    if (field == ContactField.PHONE) {
                        String email = order == null ? null : cleanEmail(order.email());
                        return new ContactRow(first, last, city, value, email == null ? "" : email);
                    }
                    String phone = order == null ? null : cleanPhone(order.phone());
                    return new ContactRow(first, last, city, phone == null ? "" : phone, value);
                })
                .toList();
    }

    /** CSV for Excel: semicolons, columns as in the old file (the main field after the city). The caller adds the UTF-8 BOM. */
    public static String toCsv(ContactField field, Collection<ContactRow> rows) {
        boolean phone = field == ContactField.PHONE;
        var csv = new StringBuilder("Ім'я;Прізвище;Місто;").append(phone ? "Телефон;E-mail" : "E-mail;Телефон").append("\r\n");
        for (ContactRow row : rows) {
            csv.append(Stream.of(row.firstName(), row.lastName(), row.city(), phone ? row.phone() : row.email(), phone ? row.email() : row.phone())
                    .map(ContactExport::escape).collect(Collectors.joining(";"))).append("\r\n");
        }
        return csv.toString();
    }

    // Quotes for fields with ; " or a line break, and an apostrophe before =+-@ so Excel does not run the value as a formula.
    private static String escape(String value) {
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return value.matches("(?s).*[;\"\r\n].*") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
