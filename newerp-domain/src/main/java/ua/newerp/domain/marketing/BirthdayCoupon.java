package ua.newerp.domain.marketing;

import java.util.HexFormat;
import java.util.Locale;
import java.util.random.RandomGenerator;

import ua.newerp.domain.common.DomainException;

/** A personal birthday discount coupon (a dd_coupon row, as the old site's createCoupon). */
public final class BirthdayCoupon {

    public static final String PREFIX = "DN";

    private BirthdayCoupon() {
    }

    /** The code DN + 8 hex characters, as the old generateCouponCode. */
    public static String newCode(RandomGenerator random) {
        return PREFIX + HexFormat.of().withUpperCase().toHexDigits(random.nextInt());
    }

    /** A coupon code: Latin letters and digits, up to 20 characters (the length of dd_coupon.code). */
    public static String requireCode(String code) {
        String trimmed = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (trimmed.length() < 4 || trimmed.length() > 20 || !trimmed.matches("[A-Z0-9]+")) {
            throw new DomainException("Код купона — від 4 до 20 латинських літер і цифр.");
        }
        return trimmed;
    }
}
