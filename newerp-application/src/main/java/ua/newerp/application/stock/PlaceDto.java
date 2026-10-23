package ua.newerp.application.stock;

import java.math.BigDecimal;

/** A place where stock lies: a warehouse of dd_warehouse, or the main stock when warehouseId is null. */
public record PlaceDto(Integer warehouseId, String name, BigDecimal quantity) {
}
