package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stock of all active items by place. places are the columns: the main stock, then the warehouses that hold anything, each
 * with the value of its stock (quantity × price). Each row's quantities follow the same order.
 */
public record StockByWarehouseDto(List<Place> places, List<Row> rows) {

    /** warehouseId null: the main stock. */
    public record Place(Integer warehouseId, String name, BigDecimal value) {
    }

    public record Row(int itemId, String name, String unit, String categoryName, BigDecimal total, List<BigDecimal> quantities) {
    }
}
