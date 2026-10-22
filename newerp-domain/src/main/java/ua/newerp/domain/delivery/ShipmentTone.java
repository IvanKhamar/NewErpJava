package ua.newerp.domain.delivery;

/** How a waybill status looks in the list: the old site coloured it the same way. */
public enum ShipmentTone {
    /** Still on the way or at the branch. */
    INFO,
    /** Received (with cash on delivery: when the money has come back to the sender). */
    SUCCESS,
    /** Return or redirection. */
    WARNING,
    /** Refused or deleted. */
    ERROR
}
