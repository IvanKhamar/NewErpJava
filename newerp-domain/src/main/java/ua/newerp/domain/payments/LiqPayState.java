package ua.newerp.domain.payments;

/** The state of an order's LiqPay payment, reduced to what matters to a manager. Sent as a number, like the .NET enum. */
public enum LiqPayState {
    /** LiqPay knows no payment with this order number: the customer has not paid yet. */
    NOT_FOUND,
    /** The payment started, but the money is not credited yet (waiting for confirmation, 3DS and so on). */
    WAITING,
    PAID,
    FAILED,
    REVERSED
}
