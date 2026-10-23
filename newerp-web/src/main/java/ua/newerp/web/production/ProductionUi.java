package ua.newerp.web.production;

import java.util.List;

import ua.newerp.application.production.ProductionLogDto;
import ua.newerp.application.production.ProductionOperation;
import ua.newerp.domain.production.ProductionLogEntry;
import ua.newerp.domain.production.ProductionModel;

/** Labels of the production pages (ProductionUi.cs). */
final class ProductionUi {

    private ProductionUi() {
    }

    static String model(int id) {
        return "production/models/" + id;
    }

    static String typeLabel(String type) {
        return switch (type) {
            case ProductionModel.CLOTHING -> "Власний пошив";
            case ProductionModel.READY_MADE -> "Готовий товар";
            default -> type;
        };
    }

    /** The button name of an operation. For ready goods "sewn" is a receipt from the supplier. */
    static String operationLabel(ProductionOperation operation, String modelType) {
        return switch (operation) {
            case CUT -> "Покроєно";
            case SEND_TO_SEWING -> "В пошив";
            case SEWN -> ProductionModel.READY_MADE.equals(modelType) ? "Прихід" : "Пошито";
            case SHIP -> "Відвантажено";
        };
    }

    /** Ready goods are neither cut nor sewn: only a receipt and a shipment. */
    static List<ProductionOperation> operationsFor(String modelType) {
        return ProductionModel.READY_MADE.equals(modelType)
                ? List.of(ProductionOperation.SEWN, ProductionOperation.SHIP)
                : List.of(ProductionOperation.values());
    }

    static String logLabel(ProductionLogDto entry) {
        boolean in = ProductionLogEntry.IN.equals(entry.direction());
        return switch (entry.stage()) {
            case ProductionLogEntry.CUT -> in ? "Покроєно" : "В пошив";
            case ProductionLogEntry.SEWN -> in ? "Пошито / прихід" : "Відвантажено / замовлення";
            default -> entry.stage() + " " + entry.direction();
        };
    }
}
