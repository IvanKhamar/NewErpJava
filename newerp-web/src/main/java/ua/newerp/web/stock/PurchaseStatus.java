package ua.newerp.web.stock;

import java.util.List;

import com.vaadin.flow.component.html.Span;

import ua.newerp.domain.stock.PurchaseOrder;

/** Names and colours of a supplier order status (PurchaseStatus.cs). */
final class PurchaseStatus {

    static final List<String> ALL = List.of(PurchaseOrder.DRAFT, PurchaseOrder.ORDERED, PurchaseOrder.RECEIVED, PurchaseOrder.CANCELLED);

    private PurchaseStatus() {
    }

    static String label(String status) {
        return switch (status) {
            case PurchaseOrder.DRAFT -> "Чернетка";
            case PurchaseOrder.ORDERED -> "Замовлено";
            case PurchaseOrder.RECEIVED -> "Отримано";
            case PurchaseOrder.CANCELLED -> "Скасовано";
            default -> status;
        };
    }

    static Span badge(String status) {
        var badge = new Span(label(status));
        badge.addClassName("status-badge");
        switch (status) {
            case PurchaseOrder.ORDERED -> badge.addClassName("tone-0");
            case PurchaseOrder.RECEIVED -> badge.addClassName("tone-1");
            default -> badge.addClassName("secondary"); // a draft or a cancelled order: nothing is happening with it
        }
        return badge;
    }
}
