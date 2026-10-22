package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.domain.delivery.SeatPreset;

/**
 * A waybill form filled in advance: recipient, city and branch from the order (city and warehouse when found in
 * the directory), declared cost = order total, cash on delivery = order total if paid on receipt.
 * canCreate = false: no API key and test waybills are off; testMode: no key, only test waybills are created.
 */
public record ShipmentFormDto(
        String firstName,
        String lastName,
        String phone,
        String cityName,
        CityDto city,
        String warehouseName,
        WarehouseDto warehouse,
        BigDecimal declaredCost,
        BigDecimal cashOnDelivery,
        List<SenderOption> senders,
        List<DropoffPointOption> dropoffPoints,
        List<SeatPreset> seatPresets,
        boolean canCreate,
        boolean testMode) {
}
