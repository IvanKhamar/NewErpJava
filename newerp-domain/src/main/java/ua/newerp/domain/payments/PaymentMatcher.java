package ua.newerp.domain.payments;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Suggests which order a payment belongs to. The old site looked only for orders with the same sum (without kopecks)
 * and showed the first five; here the order number, phone and surname in the payment purpose count too.
 */
public final class PaymentMatcher {

    /** Order numbers in a purpose: separate numbers of 3 to 7 digits ("зам. 12345", "№12345"), but not sums ("1500.00"). */
    private static final Pattern ORDER_NUMBER = Pattern.compile("(?<![\\d.,])\\d{3,7}(?!\\d|[.,]\\d)");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    private PaymentMatcher() {
    }

    public static List<Integer> orderNumbersIn(String text) {
        return ORDER_NUMBER.matcher(text == null ? "" : text).results().map(m -> Integer.parseInt(m.group())).distinct().toList();
    }

    public static List<ScoredCandidate> rank(BankPayment payment, List<OrderCandidate> candidates) {
        String text = payment.getDescription() + " " + payment.getTerminal();
        String digits = NON_DIGITS.matcher(text).replaceAll("");
        Set<Integer> numbers = Set.copyOf(orderNumbersIn(text));
        String lower = text.toLowerCase(Locale.ROOT);
        var amount = payment.getAmount();

        return candidates.stream()
                .map(c -> {
                    int reasons = MatchReason.NONE;
                    if (numbers.contains(c.orderId())) {
                        reasons |= MatchReason.ORDER_NUMBER;
                    }
                    if (c.total().compareTo(amount) == 0) {
                        reasons |= MatchReason.TOTAL;
                    } else if (c.prepayment().signum() > 0
                            && (c.prepayment().compareTo(amount) == 0 || c.total().subtract(c.prepayment()).compareTo(amount) == 0)) {
                        reasons |= MatchReason.PREPAYMENT;
                    }
                    String phone = NON_DIGITS.matcher(c.telephone()).replaceAll("");
                    if (phone.length() >= 9 && digits.contains(phone.substring(phone.length() - 9))) {
                        reasons |= MatchReason.PHONE;
                    }
                    String[] words = c.customer().strip().split("\\s+");
                    String surname = words[words.length - 1];
                    if (surname.length() >= 3 && lower.contains(surname.toLowerCase(Locale.ROOT))) {
                        reasons |= MatchReason.NAME;
                    }

                    // The number and the phone are almost unique, the sum is not (many orders share it).
                    // Orders already paid by another payment go lower.
                    int score = (MatchReason.has(reasons, MatchReason.ORDER_NUMBER) ? 50 : 0)
                            + (MatchReason.has(reasons, MatchReason.PHONE) ? 30 : 0)
                            + (MatchReason.has(reasons, MatchReason.TOTAL) ? 25 : 0)
                            + (MatchReason.has(reasons, MatchReason.PREPAYMENT) ? 20 : 0)
                            + (MatchReason.has(reasons, MatchReason.NAME) ? 15 : 0)
                            - (c.linkedPaymentId() != null && !c.linkedPaymentId().equals(payment.getId()) ? 40 : 0);
                    return new ScoredCandidate(c, reasons, score);
                })
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed()
                        .thenComparing(s -> s.order().dateAdded(), Comparator.reverseOrder()))
                .toList();
    }
}
