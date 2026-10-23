package ua.newerp.application.orders;

/** A shipping or payment method: the code (as in OpenCart) and the name. */
public record OrderMethodDto(String code, String name) {
}
