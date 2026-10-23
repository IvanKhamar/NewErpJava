package ua.newerp.application.stock;

import java.util.List;

/** What needs the storekeeper's attention: items and models below their minimum. notOrdered: items with nothing on order. */
public record StockAlertsDto(List<StockAlertItemDto> items, int notOrdered, List<StockAlertModelDto> models) {

    public boolean isEmpty() {
        return items.isEmpty() && models.isEmpty();
    }
}
