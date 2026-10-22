package ua.newerp.application.stock;

import java.util.List;

public record ReceivePurchaseOrderRequest(List<ReceiptLineRequest> lines) {
}
