package ua.newerp.application.production;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** stock: cut components in stock by size id. */
public record ComponentDto(int id, String name, int sortOrder, Map<Integer, BigDecimal> stock, List<ComponentBomLineDto> bom) {
}
