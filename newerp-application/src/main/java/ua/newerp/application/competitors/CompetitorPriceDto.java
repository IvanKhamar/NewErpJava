package ua.newerp.application.competitors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** source: auto (read from the page) or manual (typed in). */
public record CompetitorPriceDto(BigDecimal price, String source, LocalDateTime checkedAt) {
}
