package ua.newerp.application.stock;

import java.util.List;

public record CreatePurchaseOrderRequest(int supplierId, List<PurchaseOrderLineRequest> lines, String comment) {
}
