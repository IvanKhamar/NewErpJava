package ua.newerp.application.returns;

import java.util.List;

/** The return lookups: statuses, reasons and actions (refunded, exchange…). */
public record ReturnLookupsDto(List<ReturnLookupItem> statuses, List<ReturnLookupItem> reasons, List<ReturnLookupItem> actions) {
}
