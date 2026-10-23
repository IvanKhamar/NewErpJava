package ua.newerp.domain.payments;

/**
 * Why an order is suggested: what in it matches the payment. Bit flags, combined with |, sent as a number
 * like a [Flags] enum in the .NET API.
 */
public final class MatchReason {

    public static final int NONE = 0;
    /** The order number is in the payment purpose. */
    public static final int ORDER_NUMBER = 1;
    /** The payment equals the order total. */
    public static final int TOTAL = 2;
    /** The payment equals the prepayment or what is left after it. */
    public static final int PREPAYMENT = 4;
    /** The customer's phone is in the payment purpose. */
    public static final int PHONE = 8;
    /** The customer's surname is in the purpose or the payer name. */
    public static final int NAME = 16;

    private MatchReason() {
    }

    public static boolean has(int reasons, int flag) {
        return (reasons & flag) != 0;
    }
}
