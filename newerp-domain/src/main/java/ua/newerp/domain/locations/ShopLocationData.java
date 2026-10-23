package ua.newerp.domain.locations;

/** The fields of a warehouse or pickup point (dd_warehouse), already trimmed. */
public record ShopLocationData(String name, String description, String contact, String telephone, String city, String address, String type) {
}
