package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CouponDraft(String code, String name, BigDecimal discountPercent, LocalDate start, LocalDate end, LocalDateTime createdAt) {
}
