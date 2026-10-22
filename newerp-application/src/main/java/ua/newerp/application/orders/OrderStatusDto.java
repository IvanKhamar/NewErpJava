package ua.newerp.application.orders;

/** An order status from the directory. color is the old site's colour (#rrggbb) or empty. */
public record OrderStatusDto(int id, String name, String color) {
}
