package ua.newerp.infrastructure.marketing;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.marketing.BirthdayOrder;
import ua.newerp.application.marketing.ContactExportRequest;
import ua.newerp.application.marketing.CouponDraft;
import ua.newerp.application.marketing.CustomerOrderDto;
import ua.newerp.application.marketing.MarketingStore;
import ua.newerp.application.marketing.OrderPhone;
import ua.newerp.domain.marketing.ContactField;
import ua.newerp.domain.marketing.ContactSource;
import ua.newerp.domain.marketing.OrderContact;
import ua.newerp.domain.sms.SmsMessage;
import ua.newerp.infrastructure.ErpProperties;

/** Orders, coupons, questions and subscribers of the old site for the marketing pages. */
@Repository
class JdbcMarketingStore implements MarketingStore {

    private static final LocalDateTime EARLIEST = LocalDateTime.of(1000, 1, 1, 0, 0);
    private static final LocalDateTime LATEST = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcMarketingStore(JdbcClient jdbc, ErpProperties erp) {
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public List<BirthdayOrder> birthdayOrders() {
        return jdbc.sql("""
                SELECT telephone, firstname, lastname, email, shipping_city, shipping_method, instagram, birthday_day, birthday_month, date_added
                FROM dd_order WHERE birthday_day > 0 AND birthday_month > 0
                """)
                .query((rs, n) -> new BirthdayOrder(rs.getString("telephone"), rs.getString("firstname"), rs.getString("lastname"),
                        rs.getString("email"), rs.getString("shipping_city"), rs.getString("shipping_method"), rs.getString("instagram"),
                        rs.getInt("birthday_day"), rs.getInt("birthday_month"), rs.getObject("date_added", LocalDateTime.class)))
                .list();
    }

    @Override
    public List<String> birthdaySmsPhones(LocalDateTime since) {
        return jdbc.sql("SELECT phone FROM dd_sms_log WHERE pre_order_id = :marker AND date >= :since")
                .param("marker", SmsMessage.BIRTHDAY_MARKER).param("since", since)
                .query(String.class).list();
    }

    @Override
    public boolean couponExists(String code) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_coupon WHERE code = :code").param("code", code).query(Integer.class).single() > 0;
    }

    @Override
    public int addCoupon(CouponDraft coupon) {
        // As the old site's createCoupon: a percent, no shipping and no minimum total, one use.
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO dd_coupon (name, code, type, discount, logged, shipping, total, date_start, date_end, uses_total, uses_customer,
                                       status, date_added)
                VALUES (:name, :code, 'P', :discount, 0, 0, 0, :start, :end, 1, '0', 1, :now)
                """)
                .param("name", coupon.name().length() > 128 ? coupon.name().substring(0, 128) : coupon.name())
                .param("code", coupon.code()).param("discount", coupon.discountPercent())
                .param("start", coupon.start()).param("end", coupon.end()).param("now", coupon.createdAt())
                .update(keys);
        return keys.getKey().intValue();
    }

    @Override
    public List<OrderPhone> orderPhones() {
        return jdbc.sql("SELECT order_id, telephone FROM dd_order WHERE order_status_id > 0 AND telephone <> ''")
                .query((rs, n) -> new OrderPhone(rs.getInt("order_id"), rs.getString("telephone")))
                .list();
    }

    @Override
    public List<CustomerOrderDto> orders(Collection<Integer> orderIds) {
        Map<Integer, List<String>> lines = new HashMap<>();
        jdbc.sql("SELECT order_id, name, quantity FROM dd_order_product WHERE order_id IN (:ids) ORDER BY order_product_id")
                .param("ids", orderIds)
                .query(rs -> {
                    lines.computeIfAbsent(rs.getInt("order_id"), k -> new ArrayList<>()).add(rs.getString("name") + " x" + rs.getInt("quantity"));
                });
        return jdbc.sql("""
                SELECT o.order_id, o.date_added, o.total, o.payment_method, COALESCE(s.name, '') AS status
                FROM dd_order o
                LEFT JOIN dd_order_status s ON s.order_status_id = o.order_status_id AND s.language_id = :languageId
                WHERE o.order_id IN (:ids)
                ORDER BY o.date_added DESC
                """)
                .param("languageId", languageId).param("ids", orderIds)
                .query((rs, n) -> new CustomerOrderDto(rs.getInt("order_id"), rs.getObject("date_added", LocalDateTime.class),
                        rs.getBigDecimal("total"), rs.getString("payment_method"), rs.getString("status"),
                        String.join(", ", lines.getOrDefault(rs.getInt("order_id"), List.of()))))
                .list();
    }

    @Override
    public List<String> contactValues(ContactExportRequest request) {
        LocalDateTime from = request.from() == null ? EARLIEST : request.from().atStartOfDay();
        LocalDateTime to = request.to() == null ? LATEST : request.to().atTime(LocalTime.MAX);
        boolean phone = request.field() == ContactField.PHONE;
        var values = new ArrayList<String>();

        if (request.sources().contains(ContactSource.ORDERS)) {
            String column = phone ? "telephone" : "email";
            values.addAll(jdbc.sql("SELECT " + column + " FROM dd_order WHERE order_status_id > 0 AND date_added >= :from AND date_added <= :to"
                    + " AND " + column + " <> ''")
                    .param("from", from).param("to", to)
                    .query(String.class).list());
        }
        // As on the old site: questions have only a phone, subscriptions only an e-mail.
        if (phone && request.sources().contains(ContactSource.QUESTIONS)) {
            values.addAll(jdbc.sql("SELECT telephone FROM dd_question WHERE telephone <> '' AND date_added >= :from AND date_added <= :to")
                    .param("from", from).param("to", to)
                    .query(String.class).list());
        }
        if (!phone && request.sources().contains(ContactSource.SUBSCRIBERS)) {
            values.addAll(jdbc.sql("SELECT email FROM dd_subscribe WHERE email <> '' AND date_added >= :from AND date_added <= :to")
                    .param("from", from).param("to", to)
                    .query(String.class).list());
        }
        return values;
    }

    @Override
    public List<OrderContact> orderContacts() {
        return jdbc.sql("SELECT telephone, email, firstname, lastname, shipping_city, date_added FROM dd_order WHERE order_status_id > 0")
                .query((rs, n) -> new OrderContact(rs.getString("telephone"), rs.getString("email"), rs.getString("firstname"),
                        rs.getString("lastname"), rs.getString("shipping_city"), rs.getObject("date_added", LocalDateTime.class)))
                .list();
    }
}
