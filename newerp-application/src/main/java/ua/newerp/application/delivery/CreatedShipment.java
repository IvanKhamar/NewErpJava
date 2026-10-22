package ua.newerp.application.delivery;

import java.math.BigDecimal;

public record CreatedShipment(String ref, String number, BigDecimal cost) {
}
