package ua.newerp.domain.delivery;

import java.math.BigDecimal;

/** A seat (box) in a shipment: weight in kg, dimensions in cm. */
public record ShipmentSeat(BigDecimal weight, BigDecimal width, BigDecimal length, BigDecimal height) {

    /** Volume in m³, as the Nova Poshta API expects it (volumetricVolume). */
    public BigDecimal volume() {
        return width.multiply(length).multiply(height).movePointLeft(6);
    }
}
