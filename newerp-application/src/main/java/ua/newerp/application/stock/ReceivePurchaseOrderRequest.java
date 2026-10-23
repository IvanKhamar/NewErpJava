package ua.newerp.application.stock;

import java.util.List;

/** warehouseId: where the delivery goes; null (as in the .NET API) is the main stock. */
public record ReceivePurchaseOrderRequest(List<ReceiptLineRequest> lines, Integer warehouseId) {

    public ReceivePurchaseOrderRequest(List<ReceiptLineRequest> lines) {
        this(lines, null);
    }
}
