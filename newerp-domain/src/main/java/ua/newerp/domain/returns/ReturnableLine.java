package ua.newerp.domain.returns;

/** An order line a return is made from. */
public record ReturnableLine(int productId, String name, String model, int quantity) {
}
