package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.delivery.InternationalShipment;
import ua.newerp.domain.delivery.SeatPreset;

/**
 * What the international waybill form starts with: the recipient and the lines from the order (HS codes, English names and
 * weights are typed in), the order total as the declared cost, the box presets and whether a waybill can be created at all.
 *
 * @param test the waybill will be a fake one (development without a key)
 */
public record InternationalFormDto(InternationalShipment.Recipient recipient, InternationalShipment.Terms terms,
        List<InternationalShipment.Item> items, BigDecimal declaredCost, String currency, List<SeatPreset> seatPresets,
        boolean canCreate, boolean test) {

    /** A country for the select: ISO code as the id is a string, so not a LookupItem. */
    public record Country(String code, String name) {
    }
}
