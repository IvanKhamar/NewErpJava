package ua.newerp.application.competitors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A competitor's page of one of our products with the last price found; lastError is empty when the last check worked. */
public record CompetitorLinkDto(int id, int competitorId, String competitorName, int productId, String productName, String url,
        BigDecimal lastPrice, LocalDateTime lastCheckedAt, String lastError) {
}
