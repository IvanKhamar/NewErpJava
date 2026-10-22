package ua.newerp.application.stock;

import java.util.List;

public record SetPurchaseOrderLinesRequest(List<PurchaseOrderLineRequest> lines) {
}
