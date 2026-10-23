package ua.newerp.domain.marketing;

import java.math.BigDecimal;
import java.time.LocalDate;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;

/**
 * The terms of a discount coupon (a dd_coupon row of the old site): a percent or a fixed sum off the order, from a minimum order
 * total, within the dates, a limited number of uses. Checked here before it is saved; usage is counted from the orders.
 */
public record Coupon(String name, String code, String type, BigDecimal discount, BigDecimal minTotal, boolean freeShipping,
        boolean loggedOnly, LocalDate start, LocalDate end, int usesTotal, int usesPerCustomer, boolean active) {

    /** Percent off the order. */
    public static final String PERCENT = "P";
    /** A fixed sum off the order. */
    public static final String FIXED = "F";

    public static final String ACTIVE = "Active";
    public static final String DISABLED = "Disabled";
    public static final String NOT_STARTED = "NotStarted";
    public static final String EXPIRED = "Expired";
    public static final String USED_UP = "UsedUp";

    public Coupon {
        name = Text.require(name, "Назва купона", 128);
        code = BirthdayCoupon.requireCode(code);
        if (!PERCENT.equals(type) && !FIXED.equals(type)) {
            throw new DomainException("Тип знижки має бути P (відсоток) або F (сума).");
        }
        if (discount == null || discount.signum() < 0 || (discount.signum() == 0 && !freeShipping)) {
            throw new DomainException("Знижка має бути більшою за нуль (або купон дає безкоштовну доставку).");
        }
        if (PERCENT.equals(type) && discount.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DomainException("Знижка у відсотках — не більше 100.");
        }
        if (minTotal == null) {
            minTotal = BigDecimal.ZERO;
        }
        if (minTotal.signum() < 0) {
            throw new DomainException("Мінімальна сума замовлення не може бути від'ємною.");
        }
        if (start == null || end == null) {
            throw new DomainException("Вкажіть дати початку й кінця дії купона.");
        }
        if (end.isBefore(start)) {
            throw new DomainException("Кінець дії купона раніше за початок.");
        }
        if (usesTotal < 0 || usesPerCustomer < 0) {
            throw new DomainException("Кількість використань не може бути від'ємною (0 — без обмеження).");
        }
    }

    /**
     * What the coupon is on a given day, for the list: turned off, not started yet, expired, used up (usesTotal reached, 0 means
     * no limit) or active.
     */
    public static String state(boolean active, LocalDate start, LocalDate end, int usesTotal, int used, LocalDate today) {
        if (!active) {
            return DISABLED;
        }
        if (start != null && today.isBefore(start)) {
            return NOT_STARTED;
        }
        if (end != null && today.isAfter(end)) {
            return EXPIRED;
        }
        if (usesTotal > 0 && used >= usesTotal) {
            return USED_UP;
        }
        return ACTIVE;
    }
}
