package ua.newerp.application.production;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sewn pieces of every size of the active models by place. places are the columns: the main stock (warehouseId null), then the
 * warehouses that hold anything. Each row's quantities follow the same order.
 */
public record SewnByWarehouseDto(List<Place> places, List<Row> rows) {

    public record Place(Integer warehouseId, String name) {
    }

    public record Row(int productionId, String modelName, int sizeId, String sizeName, BigDecimal total, List<BigDecimal> quantities) {
    }
}
