package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.LocalDate;

/** type: P (percent) or F (fixed sum). usesTotal and usesPerCustomer: 0 means no limit. */
public record SaveCouponRequest(String name, String code, String type, BigDecimal discount, BigDecimal minTotal, boolean freeShipping,
        boolean loggedOnly, LocalDate start, LocalDate end, int usesTotal, int usesPerCustomer, boolean active) {
}
