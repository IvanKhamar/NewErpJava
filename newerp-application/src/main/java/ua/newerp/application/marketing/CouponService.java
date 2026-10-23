package ua.newerp.application.marketing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.marketing.Coupon;

/**
 * Discount coupons of the shop (the old site's marketing/coupon): the list with what each one gave, creating, changing and turning
 * off. Coupons are never deleted, so the orders keep pointing at them.
 */
@Service
@Transactional
public class CouponService {

    private final CouponStore store;
    private final Clock clock;

    public CouponService(CouponStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Newest first; state filters by Active, Disabled, NotStarted, Expired or UsedUp (null: all). */
    @Transactional(readOnly = true)
    public List<CouponDto> list(String state) {
        Map<String, CouponStore.Usage> usage = store.usage();
        LocalDate today = LocalDate.now(clock);
        return store.list().stream()
                .map(c -> dto(c, usage, today))
                .filter(c -> state == null || state.isBlank() || c.state().equalsIgnoreCase(state))
                .sorted(Comparator.comparing(CouponDto::id).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponDto get(int id) {
        return dto(require(id), store.usage(), LocalDate.now(clock));
    }

    public CouponDto create(SaveCouponRequest request) {
        Coupon coupon = coupon(request);
        requireFreeCode(coupon.code(), null);
        int id = store.add(coupon, LocalDateTime.now(clock));
        return get(id);
    }

    public CouponDto update(int id, SaveCouponRequest request) {
        require(id);
        Coupon coupon = coupon(request);
        requireFreeCode(coupon.code(), id);
        store.update(id, coupon);
        return get(id);
    }

    private CouponStore.CouponRow require(int id) {
        return store.find(id).orElseThrow(() -> new NotFoundException("Купон " + id + " не знайдено."));
    }

    private void requireFreeCode(String code, Integer exceptId) {
        if (store.codeTaken(code, exceptId)) {
            throw new DomainException("Купон з кодом " + code + " уже є.");
        }
    }

    private static Coupon coupon(SaveCouponRequest r) {
        String type = r.type() == null ? null : r.type().strip().toUpperCase(Locale.ROOT);
        return new Coupon(r.name(), r.code(), type, r.discount(), r.minTotal(), r.freeShipping(), r.loggedOnly(), r.start(), r.end(),
                r.usesTotal(), r.usesPerCustomer(), r.active());
    }

    private static CouponDto dto(CouponStore.CouponRow c, Map<String, CouponStore.Usage> usage, LocalDate today) {
        CouponStore.Usage used = usage.getOrDefault(c.code().toUpperCase(Locale.ROOT), new CouponStore.Usage(0, BigDecimal.ZERO));
        return new CouponDto(c.id(), c.name(), c.code(), c.type(), c.discount(), c.minTotal(), c.freeShipping(), c.loggedOnly(), c.start(),
                c.end(), c.usesTotal(), c.usesPerCustomer(), c.active(), c.createdAt(), used.orders(), used.discount(),
                Coupon.state(c.active(), c.start(), c.end(), c.usesTotal(), used.orders(), today));
    }
}
