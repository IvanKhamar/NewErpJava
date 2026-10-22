package ua.newerp.application.stock;

public record CreateStockSupplierRequest(String name, String phone, String email, String categories, String notes) {
}
