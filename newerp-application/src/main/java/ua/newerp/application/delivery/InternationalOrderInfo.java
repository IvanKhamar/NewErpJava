package ua.newerp.application.delivery;

import java.math.BigDecimal;
import java.util.List;

/** Order data for the international waybill form. country is the order's text, as the shop wrote it. */
public record InternationalOrderInfo(String firstName, String lastName, String telephone, String email, String country, String region,
        String city, String postCode, String address, BigDecimal total, String currencyCode, List<Line> lines) {

    public record Line(String name, int quantity, BigDecimal total) {
    }
}
