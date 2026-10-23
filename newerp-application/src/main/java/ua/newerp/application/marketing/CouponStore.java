package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ua.newerp.domain.marketing.Coupon;

/** Coupons of the old site (dd_coupon) and how placed orders used them (dd_order_total). */
public interface CouponStore {

    List<CouponRow> list();

    Optional<CouponRow> find(int id);

    /** Another coupon (not exceptId) already has this code. */
    boolean codeTaken(String code, Integer exceptId);

    int add(Coupon coupon, LocalDateTime now);

    void update(int id, Coupon coupon);

    /** Placed orders and the discount they got, by coupon code (upper case). */
    Map<String, Usage> usage();

    record CouponRow(int id, String name, String code, String type, BigDecimal discount, BigDecimal minTotal, boolean freeShipping,
            boolean loggedOnly, LocalDate start, LocalDate end, int usesTotal, int usesPerCustomer, boolean active,
            LocalDateTime createdAt) {
    }

    record Usage(int orders, BigDecimal discount) {
    }
}
