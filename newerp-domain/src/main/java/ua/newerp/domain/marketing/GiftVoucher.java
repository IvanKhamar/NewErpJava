package ua.newerp.domain.marketing;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

import ua.newerp.domain.common.DomainException;

/**
 * A gift voucher of the shop (OpenCart's dd_voucher). The site redeems it at checkout, the ERP in manual orders; every
 * spending is a dd_voucher_history row with a minus, so the balance is the amount plus the history.
 *
 * @param themeId the voucher picture of the old site (dd_voucher_theme), 0 if none
 */
public record GiftVoucher(String code, String fromName, String fromEmail, String toName, String toEmail, int themeId, String message,
        BigDecimal amount, boolean active) {

    /** dd_voucher.code is varchar(10). */
    public static final int MAX_CODE_LENGTH = 10;
    /** Generated codes: 8 characters without the look-alikes 0/O and 1/I, easy to dictate on the phone. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GENERATED_LENGTH = 8;

    /** Checks and cleans what a manager typed. The code is upper-cased; an empty one must be generated before. */
    public static GiftVoucher of(String code, String fromName, String fromEmail, String toName, String toEmail, Integer themeId, String message,
            BigDecimal amount, Boolean active) {
        String cleanCode = code == null ? "" : code.strip().toUpperCase();
        if (!cleanCode.matches("[A-Z0-9]{1," + MAX_CODE_LENGTH + "}")) {
            throw new DomainException("Код сертифіката — від 1 до " + MAX_CODE_LENGTH + " латинських літер або цифр.");
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new DomainException("Сума сертифіката — більше 0, не більше двох знаків після коми.");
        }
        if (toName == null || toName.isBlank()) {
            throw new DomainException("Вкажіть, кому сертифікат.");
        }
        String cleanToName = toName.strip();
        String cleanFromName = text(fromName);
        // dd_voucher: names varchar(64), emails varchar(96).
        if (cleanToName.length() > 64 || cleanFromName.length() > 64 || text(fromEmail).length() > 96 || text(toEmail).length() > 96) {
            throw new DomainException("Ім'я — не довше 64 символів, email — не довше 96.");
        }
        return new GiftVoucher(cleanCode, cleanFromName, text(fromEmail), cleanToName, text(toEmail), themeId == null ? 0 : themeId,
                text(message), amount, active == null || active);
    }

    public static String newCode(RandomGenerator random) {
        var code = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** @param spent the sum of the history rows (spendings are negative, refunds positive) */
    public static BigDecimal balance(BigDecimal amount, BigDecimal spent) {
        return amount.add(spent == null ? BigDecimal.ZERO : spent);
    }

    /**
     * Whether the site and the ERP accept it, as OpenCart's getVoucher: switched on and, if it was bought in an order (orderId &gt;
     * 0), that order is paid. The balance is checked apart.
     */
    public static boolean usable(boolean active, int orderId, boolean orderPaid) {
        return active && (orderId == 0 || orderPaid);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
