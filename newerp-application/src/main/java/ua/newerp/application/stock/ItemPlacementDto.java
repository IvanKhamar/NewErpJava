package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.util.List;

/** Where a consumable lies: the main stock first, then every warehouse that holds some. total = their sum. */
public record ItemPlacementDto(int itemId, String name, String unit, BigDecimal total, List<PlaceDto> places) {
}
