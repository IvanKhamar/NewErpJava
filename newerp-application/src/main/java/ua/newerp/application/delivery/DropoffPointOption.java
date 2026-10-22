package ua.newerp.application.delivery;

/** Where parcels are taken to (nova-poshta.dropoff-points). */
public record DropoffPointOption(String id, String name, boolean isDefault) {
}
