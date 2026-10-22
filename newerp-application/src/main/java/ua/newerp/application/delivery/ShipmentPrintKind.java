package ua.newerp.application.delivery;

import java.util.Arrays;

import ua.newerp.domain.common.DomainException;

public enum ShipmentPrintKind {

    /** Express waybill. */
    DOCUMENT,

    /** Markings (a sticker for the box). */
    MARKINGS;

    /** From the query string: "Document" or "Markings" as in .NET, any letter case. */
    public static ShipmentPrintKind parse(String value) {
        return Arrays.stream(values())
                .filter(k -> k.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new DomainException("kind має бути Document або Markings."));
    }
}
