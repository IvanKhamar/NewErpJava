package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A coupon with its usage: used = placed orders with this coupon, discountGiven = what they took off. state: Active, Disabled,
 * NotStarted, Expired or UsedUp. Dates are null where the old site stored 0000-00-00.
 */
public record CouponDto(int id, String name, String code, String type, BigDecimal discount, BigDecimal minTotal, boolean freeShipping,
        boolean loggedOnly, LocalDate start, LocalDate end, int usesTotal, int usesPerCustomer, boolean active, LocalDateTime createdAt,
        int used, BigDecimal discountGiven, String state) {
}
