package ua.newerp.domain.delivery;

import java.math.BigDecimal;
import java.util.List;

/** Ready-made boxes from the old site (the "small / medium / large" buttons). */
public record SeatPreset(String name, ShipmentSeat seat) {

    public static final List<SeatPreset> ALL = List.of(
            new SeatPreset("Маленька", seat(31, 36, 6)),
            new SeatPreset("Середня", seat(41, 48, 8)),
            new SeatPreset("Велика", seat(49, 61, 14)));

    private static ShipmentSeat seat(int width, int length, int height) {
        return new ShipmentSeat(BigDecimal.ONE, BigDecimal.valueOf(width), BigDecimal.valueOf(length), BigDecimal.valueOf(height));
    }
}
