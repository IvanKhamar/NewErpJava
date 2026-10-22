package ua.newerp.domain.common;

/** Checks for text fields that every module uses. */
public final class Text {

    private Text() {
    }

    /** Trims the value; it must not be empty and not longer than {@code maxLength}. */
    public static String require(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            throw new DomainException(field + ": значення обов'язкове.");
        }
        if (trimmed.length() > maxLength) {
            throw new DomainException(field + ": не довше " + maxLength + " символів.");
        }
        return trimmed;
    }

    /** Trims the value; null becomes an empty string (the old site stores "" rather than NULL). */
    public static String orEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    /** Cuts the value to {@code maxLength} characters. */
    public static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
