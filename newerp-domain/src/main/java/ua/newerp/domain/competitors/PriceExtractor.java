package ua.newerp.domain.competitors;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import ua.newerp.domain.common.DomainException;

/**
 * Finds the product price on a shop page. First the competitor's own expression (if set), then the usual markup: the
 * product:price:amount meta tag, an itemprop="price" element, the "price" of schema.org JSON-LD.
 */
public final class PriceExtractor {

    private static final List<Pattern> STANDARD = List.of(
            Pattern.compile("property=[\"']product:price:amount[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("content=[\"']([^\"']+)[\"'][^>]*property=[\"']product:price:amount[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("itemprop=[\"']price[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("content=[\"']([^\"']+)[\"'][^>]*itemprop=[\"']price[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"price\"\\s*:\\s*\"?([0-9][0-9\\s.,]*)"));
    private static final Pattern NUMBER = Pattern.compile("[0-9][0-9\\s  .,]*");

    private PriceExtractor() {
    }

    /** Checks a competitor's expression: it must compile and have a group for the price. */
    public static String requireRegex(String regex) {
        String trimmed = regex == null ? "" : regex.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > 255) {
            throw new DomainException("Вираз для ціни — не довше 255 символів.");
        }
        try {
            if (Pattern.compile(trimmed).matcher("").groupCount() < 1) {
                throw new DomainException("Вираз для ціни має містити групу в дужках, наприклад class=\"price\">([0-9 ]+).");
            }
        } catch (PatternSyntaxException e) {
            throw new DomainException("Вираз для ціни з помилкою: " + e.getDescription() + ".");
        }
        return trimmed;
    }

    public static Optional<BigDecimal> extract(String html, String customRegex) {
        if (html == null || html.isEmpty()) {
            return Optional.empty();
        }
        if (customRegex != null && !customRegex.isBlank()) {
            Optional<BigDecimal> custom = first(Pattern.compile(customRegex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL), html);
            if (custom.isPresent()) {
                return custom;
            }
        }
        for (Pattern pattern : STANDARD) {
            Optional<BigDecimal> found = first(pattern, html);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> first(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        while (m.find()) {
            Optional<BigDecimal> price = parse(m.group(1));
            if (price.isPresent()) {
                return price;
            }
        }
        return Optional.empty();
    }

    /**
     * "1 299,00", "1,299.00", "1299.5" or "1 299 грн" as a number: spaces are thousands separators; of a comma and a dot the
     * last one is the decimal point; a lone comma or dot followed by three digits separates thousands.
     */
    static Optional<BigDecimal> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        String s = m.group().replaceAll("[\\s  ]", "").replaceAll("[.,]+$", "");
        int comma = s.lastIndexOf(',');
        int dot = s.lastIndexOf('.');
        int decimal = Math.max(comma, dot);
        if (decimal >= 0) {
            String whole = s.substring(0, decimal).replaceAll("[.,]", "");
            String fraction = s.substring(decimal + 1);
            boolean onlySeparator = (comma < 0 || dot < 0) && fraction.length() == 3 && s.indexOf(s.charAt(decimal)) == decimal
                    && !whole.isEmpty();
            s = onlySeparator ? whole + fraction : whole + "." + fraction;
        }
        try {
            BigDecimal price = new BigDecimal(s);
            return price.signum() > 0 ? Optional.of(price) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
