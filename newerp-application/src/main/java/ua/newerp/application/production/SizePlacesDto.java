package ua.newerp.application.production;

import java.math.BigDecimal;
import java.util.List;

import ua.newerp.application.stock.PlaceDto;

/** Where the sewn pieces of a size lie: the main stock first, then each warehouse that holds some. total = sewn stock. */
public record SizePlacesDto(int sizeId, String name, BigDecimal total, List<PlaceDto> places) {
}
