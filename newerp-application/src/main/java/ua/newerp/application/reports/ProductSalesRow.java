package ua.newerp.application.reports;

import java.math.BigDecimal;

public record ProductSalesRow(int productId, String name, String model, int quantity, BigDecimal total) {
}
