package ua.newerp.domain.orders;

/** Sent as a number (ordinal), like the .NET enum. */
public enum CustomerRiskLevel {
    /** The customer has no orders yet. */
    NEW,
    /** There are orders and none is cancelled. */
    GREEN,
    /** Less than half of the orders are cancelled. */
    YELLOW,
    /** Half or more are cancelled: better ask for a prepayment. */
    RED
}
