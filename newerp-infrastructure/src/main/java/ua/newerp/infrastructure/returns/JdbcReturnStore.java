package ua.newerp.infrastructure.returns;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;
import ua.newerp.application.returns.CreateReturnRequest;
import ua.newerp.application.returns.NewReturnRecord;
import ua.newerp.application.returns.ReturnDetails;
import ua.newerp.application.returns.ReturnFilter;
import ua.newerp.application.returns.ReturnHistoryEntry;
import ua.newerp.application.returns.ReturnListItem;
import ua.newerp.application.returns.ReturnLookupItem;
import ua.newerp.application.returns.ReturnLookupsDto;
import ua.newerp.application.returns.ReturnSourceOrder;
import ua.newerp.application.returns.ReturnStore;
import ua.newerp.domain.returns.ReturnableLine;
import ua.newerp.infrastructure.ErpProperties;

/** OpenCart's return tables (dd_return*). date_ordered is never read: the real database can hold '0000-00-00' there. */
@Repository
class JdbcReturnStore implements ReturnStore {

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcReturnStore(JdbcClient jdbc, ErpProperties erp) {
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public PagedResult<ReturnListItem> list(ReturnFilter filter, Paging paging) {
        var where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (filter.returnId() != null) {
            where.append(" AND r.return_id = :returnId");
            params.put("returnId", filter.returnId());
        }
        if (filter.orderId() != null) {
            where.append(" AND r.order_id = :orderId");
            params.put("orderId", filter.orderId());
        }
        if (filter.statusId() != null) {
            where.append(" AND r.return_status_id = :statusId");
            params.put("statusId", filter.statusId());
        }
        if (filter.search() != null) {
            where.append(" AND (LOCATE(:term, CONCAT(r.firstname, ' ', r.lastname)) > 0 OR LOCATE(:term, r.telephone) > 0"
                    + " OR LOCATE(:term, r.product) > 0 OR LOCATE(:term, r.model) > 0)");
            params.put("term", filter.search());
        }

        int total = jdbc.sql("SELECT COUNT(*) FROM dd_return r" + where).params(params).query(Integer.class).single();
        ReturnLookupsDto lookups = lookups();
        Map<Integer, String> statuses = names(lookups.statuses());
        Map<Integer, String> actions = names(lookups.actions());
        params.put("limit", paging.pageSize());
        params.put("offset", paging.offset());
        List<ReturnListItem> items = jdbc.sql("""
                SELECT r.return_id, r.order_id, r.firstname, r.lastname, r.telephone, r.product, r.model, r.quantity, r.return_status_id,
                       r.return_action_id, r.date_added, r.date_modified
                FROM dd_return r""" + where + " ORDER BY r.return_id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> new ReturnListItem(rs.getInt("return_id"), rs.getInt("order_id"),
                        (rs.getString("firstname") + " " + rs.getString("lastname")).strip(), rs.getString("telephone"), rs.getString("product"),
                        rs.getString("model"), rs.getInt("quantity"), rs.getInt("return_status_id"), statuses.get(rs.getInt("return_status_id")),
                        actions.get(rs.getInt("return_action_id")), rs.getObject("date_added", LocalDateTime.class),
                        rs.getObject("date_modified", LocalDateTime.class)))
                .list();
        return PagedResult.of(items, paging.page(), paging.pageSize(), total);
    }

    @Override
    public Optional<ReturnDetails> get(int returnId) {
        ReturnLookupsDto lookups = lookups();
        Map<Integer, String> statuses = names(lookups.statuses());
        Map<Integer, String> reasons = names(lookups.reasons());
        Map<Integer, String> actions = names(lookups.actions());
        List<ReturnHistoryEntry> history = jdbc.sql("""
                SELECT date_added, return_status_id, comment FROM dd_return_history WHERE return_id = :id
                ORDER BY date_added, return_history_id
                """)
                .param("id", returnId)
                .query((rs, n) -> new ReturnHistoryEntry(rs.getObject("date_added", LocalDateTime.class), rs.getInt("return_status_id"),
                        statuses.get(rs.getInt("return_status_id")), rs.getString("comment")))
                .list();

        return jdbc.sql("""
                SELECT return_id, order_id, product_id, firstname, lastname, telephone, email, product, model, quantity, opened,
                       return_reason_id, return_action_id, return_status_id, COALESCE(comment, '') AS comment, date_added, date_modified
                FROM dd_return WHERE return_id = :id
                """)
                .param("id", returnId)
                .query((rs, n) -> new ReturnDetails(rs.getInt("return_id"), rs.getInt("order_id"), rs.getInt("product_id"),
                        (rs.getString("firstname") + " " + rs.getString("lastname")).strip(), rs.getString("telephone"), rs.getString("email"),
                        rs.getString("product"), rs.getString("model"), rs.getInt("quantity"), rs.getBoolean("opened"),
                        rs.getInt("return_reason_id"), reasons.get(rs.getInt("return_reason_id")),
                        rs.getInt("return_action_id"), actions.get(rs.getInt("return_action_id")),
                        rs.getInt("return_status_id"), statuses.get(rs.getInt("return_status_id")), rs.getString("comment"),
                        rs.getObject("date_added", LocalDateTime.class), rs.getObject("date_modified", LocalDateTime.class), history))
                .optional();
    }

    @Override
    public ReturnLookupsDto lookups() {
        return new ReturnLookupsDto(lookup("dd_return_status", "return_status_id"), lookup("dd_return_reason", "return_reason_id"),
                lookup("dd_return_action", "return_action_id"));
    }

    @Override
    public Optional<ReturnSourceOrder> getSourceOrder(int orderId) {
        List<ReturnableLine> lines = jdbc.sql("SELECT product_id, name, model, quantity FROM dd_order_product WHERE order_id = :id ORDER BY order_product_id")
                .param("id", orderId)
                .query((rs, n) -> new ReturnableLine(rs.getInt("product_id"), rs.getString("name"), rs.getString("model"), rs.getInt("quantity")))
                .list();
        Map<Integer, Integer> returned = new HashMap<>();
        jdbc.sql("SELECT product_id, SUM(quantity) AS quantity FROM dd_return WHERE order_id = :id GROUP BY product_id")
                .param("id", orderId)
                .query(rs -> {
                    returned.put(rs.getInt("product_id"), rs.getInt("quantity"));
                });

        return jdbc.sql("SELECT firstname, lastname, telephone, email, customer_id, date_added FROM dd_order WHERE order_id = :id")
                .param("id", orderId)
                .query((rs, n) -> new ReturnSourceOrder(orderId, rs.getString("firstname"), rs.getString("lastname"), rs.getString("telephone"),
                        rs.getString("email"), rs.getInt("customer_id"), rs.getObject("date_added", LocalDateTime.class), lines, returned))
                .optional();
    }

    @Override
    public int create(NewReturnRecord record) {
        ReturnSourceOrder order = record.order();
        CreateReturnRequest request = record.request();
        String comment = request.comment() == null ? "" : request.comment().strip();
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO dd_return (order_id, product_id, customer_id, firstname, lastname, email, telephone, product, model, quantity,
                                       opened, return_reason_id, return_action_id, return_status_id, comment, date_ordered, date_added,
                                       date_modified)
                VALUES (:orderId, :productId, :customerId, :firstname, :lastname, :email, :telephone, :product, :model, :quantity,
                        :opened, :reasonId, :actionId, :statusId, :comment, :dateOrdered, :now, :now)
                """)
                .param("orderId", order.orderId()).param("productId", record.line().productId()).param("customerId", order.customerId())
                .param("firstname", order.firstname().strip()).param("lastname", order.lastname().strip()).param("email", order.email().strip())
                .param("telephone", order.telephone().strip()).param("product", record.line().name()).param("model", record.line().model())
                .param("quantity", request.quantity()).param("opened", request.opened()).param("reasonId", request.reasonId())
                .param("actionId", request.actionId()).param("statusId", request.statusId()).param("comment", comment)
                .param("dateOrdered", order.dateAdded().toLocalDate()).param("now", record.now())
                .update(keys);
        int id = keys.getKey().intValue();

        // As the old site's form: the first status goes to the log too.
        addLogEntry(id, request.statusId(), comment, record.now());
        return id;
    }

    @Override
    public boolean addHistory(int returnId, int statusId, String comment, Integer actionId, LocalDateTime now) {
        // MyISAM has no transactions, so the status first (as the old site's addReturnHistory), then the log.
        int updated = jdbc.sql("""
                UPDATE dd_return SET return_status_id = :statusId, return_action_id = COALESCE(:actionId, return_action_id),
                                     date_modified = :now
                WHERE return_id = :id
                """)
                .param("statusId", statusId).param("actionId", actionId).param("now", now).param("id", returnId)
                .update();
        if (updated == 0) {
            return false;
        }
        addLogEntry(returnId, statusId, comment, now);
        return true;
    }

    private void addLogEntry(int returnId, int statusId, String comment, LocalDateTime now) {
        jdbc.sql("""
                INSERT INTO dd_return_history (return_id, return_status_id, notify, comment, date_added)
                VALUES (:id, :statusId, 0, :comment, :now)
                """)
                .param("id", returnId).param("statusId", statusId).param("comment", comment).param("now", now)
                .update();
    }

    private List<ReturnLookupItem> lookup(String table, String idColumn) {
        return jdbc.sql("SELECT " + idColumn + " AS id, name FROM " + table + " WHERE language_id = :languageId ORDER BY " + idColumn)
                .param("languageId", languageId)
                .query((rs, n) -> new ReturnLookupItem(rs.getInt("id"), rs.getString("name")))
                .list();
    }

    private static Map<Integer, String> names(List<ReturnLookupItem> items) {
        Map<Integer, String> names = new HashMap<>();
        items.forEach(i -> names.put(i.id(), i.name()));
        return names;
    }
}
