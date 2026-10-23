package ua.newerp.domain.cashbox;

/** A product on the receipt. Money in kopecks, quantity in thousandths (that is how the Checkbox API takes them). */
public record ReceiptGood(String code, String name, long priceKopecks, long quantityThousandths) {
}
