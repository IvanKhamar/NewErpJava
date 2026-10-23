package ua.newerp.infrastructure.marketing;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.marketing.CouponStore;
import ua.newerp.domain.marketing.Coupon;

/** dd_coupon of the old site; usage comes from the "coupon" lines of placed orders' totals. */
@Repository
class JdbcCouponStore implements CouponStore {

    /** The old site's order total title: "Купон (CODE)"; the code is the last word in brackets. */
    private static final Pattern CODE_IN_TITLE = Pattern.compile("\\(([^()]+)\\)\\s*$");

    // The old site may hold 0000-00-00 dates, which the driver cannot read: they come back as NULL
    private static final String COLUMNS = """
            coupon_id, name, code, type, discount, total, shipping, logged,
            IF(date_start < '1000-01-01', NULL, date_start) AS date_start, IF(date_end < '1000-01-01', NULL, date_end) AS date_end,
            uses_total, uses_customer, status, IF(date_added < '1000-01-01', NULL, date_added) AS date_added
            """;

    private final JdbcClient jdbc;

    JdbcCouponStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CouponRow> list() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM dd_coupon").query((rs, n) -> row(rs)).list();
    }

    @Override
    public Optional<CouponRow> find(int id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM dd_coupon WHERE coupon_id = :id").param("id", id).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public boolean codeTaken(String code, Integer exceptId) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_coupon WHERE code = :code AND coupon_id <> :except")
                .param("code", code).param("except", exceptId == null ? 0 : exceptId)
                .query(Integer.class).single() > 0;
    }

    @Override
    public int add(Coupon coupon, LocalDateTime now) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO dd_coupon (name, code, type, discount, logged, shipping, total, date_start, date_end, uses_total, uses_customer,
                                       status, date_added)
                VALUES (:name, :code, :type, :discount, :logged, :shipping, :total, :start, :end, :usesTotal, :usesCustomer, :status, :now)
                """)
                .params(params(coupon)).param("now", now)
                .update(keys);
        return keys.getKey().intValue();
    }

    @Override
    public void update(int id, Coupon coupon) {
        jdbc.sql("""
                UPDATE dd_coupon SET name = :name, code = :code, type = :type, discount = :discount, logged = :logged, shipping = :shipping,
                       total = :total, date_start = :start, date_end = :end, uses_total = :usesTotal, uses_customer = :usesCustomer,
                       status = :status
                WHERE coupon_id = :id
                """)
                .params(params(coupon)).param("id", id)
                .update();
    }

    @Override
    public Map<String, Usage> usage() {
        Map<String, Set<Integer>> orders = new HashMap<>();
        Map<String, BigDecimal> discount = new HashMap<>();
        jdbc.sql("""
                SELECT t.order_id, t.title, t.value FROM dd_order_total t
                JOIN dd_order o ON o.order_id = t.order_id AND o.order_status_id > 0
                WHERE t.code = 'coupon'
                """)
                .query(rs -> {
                    Matcher m = CODE_IN_TITLE.matcher(rs.getString("title"));
                    if (m.find()) {
                        String code = m.group(1).strip().toUpperCase(Locale.ROOT);
                        orders.computeIfAbsent(code, k -> new HashSet<>()).add(rs.getInt("order_id"));
                        discount.merge(code, rs.getBigDecimal("value").abs(), BigDecimal::add);
                    }
                });
        Map<String, Usage> result = new HashMap<>();
        orders.forEach((code, ids) -> result.put(code, new Usage(ids.size(), discount.get(code))));
        return result;
    }

    private static Map<String, Object> params(Coupon c) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", c.name());
        p.put("code", c.code());
        p.put("type", c.type());
        p.put("discount", c.discount());
        p.put("logged", c.loggedOnly());
        p.put("shipping", c.freeShipping());
        p.put("total", c.minTotal());
        p.put("start", c.start());
        p.put("end", c.end());
        p.put("usesTotal", c.usesTotal());
        p.put("usesCustomer", String.valueOf(c.usesPerCustomer()));
        p.put("status", c.active());
        return p;
    }

    private static CouponRow row(ResultSet rs) throws SQLException {
        return new CouponRow(rs.getInt("coupon_id"), rs.getString("name"), rs.getString("code"), rs.getString("type"),
                rs.getBigDecimal("discount"), rs.getBigDecimal("total"), rs.getBoolean("shipping"), rs.getBoolean("logged"),
                rs.getObject("date_start", LocalDate.class), rs.getObject("date_end", LocalDate.class), rs.getInt("uses_total"),
                usesPerCustomer(rs.getString("uses_customer")), rs.getBoolean("status"), rs.getObject("date_added", LocalDateTime.class));
    }

    /** uses_customer is a varchar on the old site; anything that is not a number counts as no limit. */
    private static int usesPerCustomer(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
