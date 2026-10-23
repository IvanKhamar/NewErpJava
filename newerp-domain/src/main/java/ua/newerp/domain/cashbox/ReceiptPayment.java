package ua.newerp.domain.cashbox;

public record ReceiptPayment(ReceiptPaymentType type, String label, long valueKopecks) {
}
