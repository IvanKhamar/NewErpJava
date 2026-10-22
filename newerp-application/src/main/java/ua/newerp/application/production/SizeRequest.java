package ua.newerp.application.production;

import java.math.BigDecimal;

public record SizeRequest(String name, BigDecimal percent) {

    public SizeRequest {
        percent = percent == null ? BigDecimal.ZERO : percent;
    }
}
