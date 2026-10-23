package ua.newerp.application.stock;

import java.math.BigDecimal;

/**
 * How fast a consumable goes. perDay = used / days of the period. daysLeft: how long the stock lasts at that pace, null when
 * nothing was used. suggested: what to order so that stock plus what is already on order covers the next coverDays.
 */
public record ItemConsumptionDto(int itemId, String name, String unit, BigDecimal used, BigDecimal received,
        BigDecimal corrected, BigDecimal perDay, BigDecimal quantity, BigDecimal onOrder, Integer daysLeft, BigDecimal suggested,
        BigDecimal sum) {
}
