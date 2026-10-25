package ua.newerp.infrastructure.marketing;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.marketing.VoucherCardDto;
import ua.newerp.application.marketing.VoucherStore;
import ua.newerp.domain.marketing.GiftVoucher;
import ua.newerp.infrastructure.ErpProperties;

/** OpenCart's voucher tables in plain SQL (MyISAM, no transactions), the same rows the site reads at checkout. */
@Repository
class JdbcVoucherStore implements VoucherStore {

    private static final int MAX_ROWS = 500;

    // The spent sum and the status of the order a voucher was bought in, in one row per voucher.
    private static final String SELECT = """
            SELECT v.voucher_id, v.order_id, v.code, v.from_name, v.from_email, v.to_name, v.to_email, v.voucher_theme_id,
                   COALESCE(td.name, '') AS theme_name, v.message, v.amount, v.status, v.date_added,
                   COALESCE(h.spent, 0) AS spent, o.order_status_id
            FROM dd_voucher v
            LEFT JOIN (SELECT voucher_id, SUM(amount) AS spent FROM dd_voucher_history GROUP BY voucher_id) h ON h.voucher_id = v.voucher_id
            LEFT JOIN dd_voucher_theme_description td ON td.voucher_theme_id = v.voucher_theme_id AND td.language_id = :lang
            LEFT JOIN dd_order o ON v.order_id > 0 AND o.order_id = v.order_id
            """;

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcVoucherStore(JdbcClient jdbc, ErpProperties properties) {
        this.jdbc = jdbc;
        this.languageId = properties.languageId();
    }

    @Override
    public List<VoucherRow> list(String search) {
        String like = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql(SELECT + """
                WHERE (:search = '' OR v.code LIKE :like OR v.to_name LIKE :like OR v.from_name LIKE :like)
                ORDER BY v.voucher_id DESC LIMIT :limit
                """)
                .param("lang", languageId).param("search", search).param("like", like).param("limit", MAX_ROWS)
                .query((rs, n) -> row(rs))
                .list();
    }

    @Override
    public Optional<VoucherRow> find(int id) {
        return jdbc.sql(SELECT + " WHERE v.voucher_id = :id").param("lang", languageId).param("id", id).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public Optional<VoucherRow> findByCode(String code) {
        // The newest one if a code repeats (OpenCart does not stop that).
        return jdbc.sql(SELECT + " WHERE v.code = :code ORDER BY v.voucher_id DESC LIMIT 1")
                .param("lang", languageId).param("code", code).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public boolean codeTaken(String code, Integer exceptId) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_voucher WHERE code = :code AND voucher_id <> :except")
                .param("code", code).param("except", exceptId == null ? 0 : exceptId)
                .query(Integer.class).single() > 0;
    }

    @Override
    public int add(GiftVoucher v, LocalDateTime now) {
        var key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO dd_voucher (order_id, code, from_name, from_email, to_name, to_email, voucher_theme_id, message, amount, status, date_added)
                VALUES (0, :code, :fromName, :fromEmail, :toName, :toEmail, :theme, :message, :amount, :status, :now)
                """)
                .params(params(v)).param("now", now)
                .update(key);
        return key.getKey().intValue();
    }

    @Override
    public void update(int id, GiftVoucher v) {
        jdbc.sql("""
                UPDATE dd_voucher SET code = :code, from_name = :fromName, from_email = :fromEmail, to_name = :toName, to_email = :toEmail,
                       voucher_theme_id = :theme, message = :message, amount = :amount, status = :status
                WHERE voucher_id = :id
                """)
                .params(params(v)).param("id", id)
                .update();
    }

    @Override
    public void delete(int id) {
        jdbc.sql("DELETE FROM dd_voucher WHERE voucher_id = :id").param("id", id).update();
    }

    @Override
    public List<VoucherCardDto.HistoryRow> history(int id) {
        return jdbc.sql("""
                SELECT h.order_id, COALESCE(CONCAT(o.firstname, ' ', o.lastname), '') AS customer, h.amount, h.date_added
                FROM dd_voucher_history h LEFT JOIN dd_order o ON o.order_id = h.order_id
                WHERE h.voucher_id = :id ORDER BY h.voucher_history_id
                """)
                .param("id", id)
                .query((rs, n) -> new VoucherCardDto.HistoryRow(rs.getInt("order_id"), rs.getString("customer"), rs.getBigDecimal("amount"),
                        rs.getObject("date_added", LocalDateTime.class)))
                .list();
    }

    @Override
    public List<LookupItem> themes() {
        return jdbc.sql("""
                SELECT t.voucher_theme_id, COALESCE(d.name, CONCAT('#', t.voucher_theme_id)) AS name FROM dd_voucher_theme t
                LEFT JOIN dd_voucher_theme_description d ON d.voucher_theme_id = t.voucher_theme_id AND d.language_id = :lang
                ORDER BY name
                """)
                .param("lang", languageId)
                .query((rs, n) -> new LookupItem(rs.getInt("voucher_theme_id"), rs.getString("name")))
                .list();
    }

    @Override
    public Optional<OrderSums> orderSums(int orderId) {
        return jdbc.sql("SELECT total, prepayment FROM dd_order WHERE order_id = :id AND order_status_id > 0")
                .param("id", orderId)
                .query((rs, n) -> new OrderSums(rs.getBigDecimal("total"), rs.getBigDecimal("prepayment")))
                .optional();
    }

    @Override
    public Optional<SpentInOrder> spentInOrder(int orderId) {
        return jdbc.sql("""
                SELECT voucher_id, -SUM(amount) AS spent FROM dd_voucher_history WHERE order_id = :id
                GROUP BY voucher_id HAVING spent <> 0 ORDER BY voucher_id LIMIT 1
                """)
                .param("id", orderId)
                .query((rs, n) -> new SpentInOrder(rs.getInt("voucher_id"), rs.getBigDecimal("spent")))
                .optional();
    }

    // OpenCart tables are MyISAM, without transactions: the same writes as the shop's checkout, one after another. The voucher
    // line goes right before the total (sort 98 and 99), as OpenCart sorts them.
    @Override
    public void redeem(int orderId, int voucherId, String code, BigDecimal amount, LocalDateTime now) {
        jdbc.sql("INSERT INTO dd_voucher_history (voucher_id, order_id, amount, date_added) VALUES (:voucher, :order, :amount, :now)")
                .param("voucher", voucherId).param("order", orderId).param("amount", amount.negate()).param("now", now).update();
        jdbc.sql("""
                INSERT INTO dd_order_total (order_id, code, title, value, sort_order)
                VALUES (:order, 'voucher', :title, :value, 98)
                """)
                .param("order", orderId).param("title", "Подарунковий сертифікат (" + code + ")").param("value", amount.negate()).update();
        changeTotal(orderId, amount.negate());
    }

    @Override
    public void release(int orderId, int voucherId, BigDecimal amount) {
        jdbc.sql("DELETE FROM dd_voucher_history WHERE order_id = :order AND voucher_id = :voucher")
                .param("order", orderId).param("voucher", voucherId).update();
        jdbc.sql("DELETE FROM dd_order_total WHERE order_id = :order AND code = 'voucher'").param("order", orderId).update();
        changeTotal(orderId, amount);
    }

    private void changeTotal(int orderId, BigDecimal by) {
        jdbc.sql("UPDATE dd_order_total SET value = value + :by, sort_order = 99 WHERE order_id = :order AND code = 'total'")
                .param("by", by).param("order", orderId).update();
        jdbc.sql("UPDATE dd_order SET total = total + :by WHERE order_id = :order").param("by", by).param("order", orderId).update();
    }

    private static Map<String, Object> params(GiftVoucher v) {
        return Map.of("code", v.code(), "fromName", v.fromName(), "fromEmail", v.fromEmail(), "toName", v.toName(),
                "toEmail", v.toEmail(), "theme", v.themeId(), "message", v.message(), "amount", v.amount(), "status", v.active() ? 1 : 0);
    }

    private static VoucherRow row(ResultSet rs) throws SQLException {
        int orderStatus = rs.getInt("order_status_id");
        Integer orderStatusId = rs.wasNull() ? null : orderStatus;
        return new VoucherRow(rs.getInt("voucher_id"), rs.getInt("order_id"), rs.getString("code"), rs.getString("from_name"),
                rs.getString("from_email"), rs.getString("to_name"), rs.getString("to_email"), rs.getInt("voucher_theme_id"),
                rs.getString("theme_name"), rs.getString("message"), rs.getBigDecimal("amount"), rs.getInt("status") == 1,
                rs.getObject("date_added", LocalDateTime.class), rs.getBigDecimal("spent"), orderStatusId);
    }
}
