package ua.newerp.application.locations;

public record SaveLocationRequest(String name, String description, String contact, String telephone, String city, String address, String type) {
}
