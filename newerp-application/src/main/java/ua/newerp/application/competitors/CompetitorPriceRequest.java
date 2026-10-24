package ua.newerp.application.competitors;

import java.math.BigDecimal;

/** A price typed in by hand, for a site that does not let the check read it. */
public record CompetitorPriceRequest(BigDecimal price) {
}
