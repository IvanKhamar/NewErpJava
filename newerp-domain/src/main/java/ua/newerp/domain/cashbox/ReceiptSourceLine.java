package ua.newerp.domain.cashbox;

import java.math.BigDecimal;

/** An order line the receipt is built from. */
public record ReceiptSourceLine(int productId, String name, BigDecimal price, int quantity) {
}
