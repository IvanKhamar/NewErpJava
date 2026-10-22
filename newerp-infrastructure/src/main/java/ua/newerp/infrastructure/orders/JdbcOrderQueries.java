package ua.newerp.infrastructure.orders;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;
import ua.newerp.application.orders.OrderDetails;
import ua.newerp.application.orders.OrderFilter;
import ua.newerp.application.orders.OrderListItem;
import ua.newerp.application.orders.OrderProductLine;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.application.orders.OrderStatusHistoryEntry;
import ua.newerp.infrastructure.ErpProperties;

/**
 * Read side of orders in plain SQL through Spring's JdbcClient (a thin layer like Dapper).
 * Lists and cards go straight into response records; changes go through JPA and the domain.
 * Every value goes in as a :named parameter, never glued into the SQL string.
 */
@Repository
@Transactional(readOnly = true)
class JdbcOrderQueries implements OrderQueries {

    private final JdbcClient jdbc;
    private final int languageId;

    // Constructor injection: Spring sees the constructor and passes the beans in, like in ASP.NET Core.
    JdbcOrderQueries(JdbcClient jdbc, ErpProperties properties) {
        this.jdbc = jdbc;
        this.languageId = properties.languageId();
    }

    @Override
    public PagedResult<OrderListItem> list(OrderFilter filter) {
        Paging paging = Paging.normalize(filter.page(), filter.pageSize());

        // OpenCart keeps orders with order_status_id = 0 as "unfinished" (cart not checked out); they are not shown.
        StringBuilder where = new StringBuilder(" WHERE o.order_status_id > 0");
        Map<String, Object> params = new HashMap<>();
        params.put("lang", languageId);

        if (filter.statusId() != null) {
            where.append(" AND o.order_status_id = :statusId");
            params.put("statusId", filter.statusId());
        }
        if (filter.sourceId() != null) {
            where.append(" AND o.order_source_id = :sourceId");
            params.put("sourceId", filter.sourceId());
        }
        if (filter.managerId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM erp_order_manager fm WHERE fm.order_id = o.order_id AND fm.user_id = :managerId)");
            params.put("managerId", filter.managerId());
        }
        if (filter.from() != null) {
            where.append(" AND o.date_added >= :from");
            params.put("from", filter.from().atStartOfDay());
        }
        if (filter.to() != null) {
            where.append(" AND o.date_added < :to");
            params.put("to", filter.to().plusDays(1).atStartOfDay());
        }
        String search = filter.search() == null ? "" : filter.search().strip();
        if (!search.isEmpty()) {
            params.put("like", "%" + escapeLike(search) + "%");
            if (search.length() == 14 && search.chars().allMatch(c -> c >= '0' && c <= '9')) {
                // 14 digits is a Nova Poshta waybill number: find the order it belongs to.
                where.append(" AND EXISTS (SELECT 1 FROM dd_novaposhta_document d WHERE d.order_id = o.order_id AND d.number = :ttn)");
                params.put("ttn", search);
            } else if (isInt(search)) {
                where.append(" AND (o.order_id = :orderId OR o.telephone LIKE :like)");
                params.put("orderId", Integer.parseInt(search));
            } else {
                where.append(" AND (o.telephone LIKE :like OR o.lastname LIKE :like OR o.firstname LIKE :like OR o.email LIKE :like)");
            }
        }

        int total = jdbc.sql("SELECT COUNT(*) FROM dd_order o" + where).params(params).query(Integer.class).single();

        // Left joins with directories: an order is shown even if its status or manager is missing.
        params.put("limit", paging.pageSize());
        params.put("offset", paging.offset());
        List<OrderListItem> items = jdbc.sql("""
                SELECT o.order_id, o.date_added, CONCAT(o.firstname, ' ', o.lastname) AS customer, o.telephone,
                       o.total, o.currency_code, o.order_status_id, st.name AS status_name, src.name AS source_name,
                       m.user_id AS manager_id, CONCAT(u.firstname, ' ', u.lastname) AS manager_name
                FROM dd_order o
                LEFT JOIN dd_order_status st ON st.order_status_id = o.order_status_id AND st.language_id = :lang
                LEFT JOIN dd_order_source src ON src.order_source_id = o.order_source_id AND src.language_id = :lang
                LEFT JOIN erp_order_manager m ON m.order_id = o.order_id
                LEFT JOIN dd_user u ON u.user_id = m.user_id
                """ + where + """
                 ORDER BY o.date_added DESC, o.order_id DESC
                 LIMIT :limit OFFSET :offset
                """)
                .params(params)
                .query((rs, n) -> new OrderListItem(
                        rs.getInt("order_id"),
                        rs.getTimestamp("date_added").toLocalDateTime(),
                        rs.getString("customer"),
                        rs.getString("telephone"),
                        rs.getBigDecimal("total"),
                        rs.getString("currency_code"),
                        rs.getInt("order_status_id"),
                        rs.getString("status_name"),
                        rs.getString("source_name"),
                        rs.getObject("manager_id", Integer.class),
                        rs.getString("manager_name")))
                .list();

        return PagedResult.of(items, paging.page(), paging.pageSize(), total);
    }

    @Override
    public Optional<OrderDetails> get(int orderId) {
        record Head(int id, LocalDateTime dateAdded, String customer, String telephone, String email,
                String shippingMethod, String shippingCity, String shippingAddress, String paymentMethod, String comment,
                BigDecimal total, String currencyCode, int statusId, int sourceId) {
        }

        Optional<Head> head = jdbc.sql("""
                SELECT order_id, date_added, firstname, lastname, telephone, email, shipping_method, shipping_city,
                       shipping_address_1, shipping_address_2, payment_method, comment, total, currency_code,
                       order_status_id, order_source_id
                FROM dd_order WHERE order_id = :id
                """)
                .param("id", orderId)
                .query((rs, n) -> new Head(
                        rs.getInt("order_id"),
                        rs.getTimestamp("date_added").toLocalDateTime(),
                        rs.getString("firstname") + " " + rs.getString("lastname"),
                        rs.getString("telephone"),
                        rs.getString("email"),
                        rs.getString("shipping_method"),
                        rs.getString("shipping_city"),
                        (rs.getString("shipping_address_1") + " " + rs.getString("shipping_address_2")).strip(),
                        rs.getString("payment_method"),
                        rs.getString("comment"),
                        rs.getBigDecimal("total"),
                        rs.getString("currency_code"),
                        rs.getInt("order_status_id"),
                        rs.getInt("order_source_id")))
                .optional();
        if (head.isEmpty()) {
            return Optional.empty();
        }
        Head o = head.get();

        Map<Integer, String> statusNames = new HashMap<>();
        listStatuses().forEach(s -> statusNames.put(s.id(), s.name()));

        String sourceName = jdbc.sql("SELECT name FROM dd_order_source WHERE order_source_id = :id AND language_id = :lang")
                .param("id", o.sourceId())
                .param("lang", languageId)
                .query(String.class)
                .optional()
                .orElse(null);

        record Manager(int userId, String name) {
        }
        Manager manager = jdbc.sql("""
                SELECT m.user_id, CONCAT(u.firstname, ' ', u.lastname) AS name
                FROM erp_order_manager m JOIN dd_user u ON u.user_id = m.user_id
                WHERE m.order_id = :id
                """)
                .param("id", orderId)
                .query((rs, n) -> new Manager(rs.getInt("user_id"), rs.getString("name")))
                .optional()
                .orElse(null);

        List<OrderProductLine> products = jdbc.sql("""
                SELECT product_id, name, model, quantity, price, total
                FROM dd_order_product WHERE order_id = :id ORDER BY order_product_id
                """)
                .param("id", orderId)
                .query((rs, n) -> new OrderProductLine(rs.getInt("product_id"), rs.getString("name"), rs.getString("model"),
                        rs.getInt("quantity"), rs.getBigDecimal("price"), rs.getBigDecimal("total")))
                .list();

        List<OrderStatusHistoryEntry> history = jdbc.sql("""
                SELECT l.date_added, l.old_status_id, l.new_status_id, l.user_id,
                       CONCAT(u.firstname, ' ', u.lastname) AS user_name
                FROM dd_order_status_log l LEFT JOIN dd_user u ON u.user_id = l.user_id
                WHERE l.order_id = :id
                ORDER BY l.date_added, l.order_status_log_id
                """)
                .param("id", orderId)
                .query((rs, n) -> new OrderStatusHistoryEntry(
                        rs.getTimestamp("date_added").toLocalDateTime(),
                        rs.getInt("old_status_id"),
                        rs.getInt("new_status_id"),
                        statusNames.get(rs.getInt("new_status_id")),
                        rs.getInt("user_id"),
                        rs.getString("user_name")))
                .list();

        return Optional.of(new OrderDetails(o.id(), o.dateAdded(), o.customer(), o.telephone(), o.email(),
                o.shippingMethod(), o.shippingCity(), o.shippingAddress(), o.paymentMethod(), o.comment(), o.total(),
                o.currencyCode(), o.statusId(), statusNames.get(o.statusId()), o.sourceId(), sourceName,
                manager == null ? null : manager.userId(), manager == null ? null : manager.name(), products, history));
    }

    @Override
    public List<OrderStatusDto> listStatuses() {
        return jdbc.sql("SELECT order_status_id, name, color FROM dd_order_status WHERE language_id = :lang ORDER BY order_status_id")
                .param("lang", languageId)
                .query((rs, n) -> new OrderStatusDto(rs.getInt("order_status_id"), rs.getString("name"), rs.getString("color")))
                .list();
    }

    @Override
    public List<LookupItem> listSources() {
        return jdbc.sql("SELECT order_source_id, name FROM dd_order_source WHERE language_id = :lang ORDER BY order_source_id")
                .param("lang", languageId)
                .query((rs, n) -> new LookupItem(rs.getInt("order_source_id"), rs.getString("name")))
                .list();
    }

    @Override
    public List<LookupItem> listManagers() {
        List<LookupItem> users = new ArrayList<>(jdbc.sql("SELECT user_id, firstname, lastname FROM dd_user WHERE status = 1")
                .query((rs, n) -> new LookupItem(rs.getInt("user_id"),
                        (rs.getString("firstname") + " " + rs.getString("lastname")).strip()))
                .list());
        // Collator sorts by the rules of a language (є after е, і after и), like StringComparer.CurrentCulture
        Collator ukrainian = Collator.getInstance(Locale.of("uk", "UA"));
        users.sort(Comparator.comparing(LookupItem::name, ukrainian));
        return users;
    }

    /** LIKE treats % and _ as wildcards; escape them so a search for "50%" finds exactly that. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean isInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
