package ua.newerp.infrastructure.payments;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;
import ua.newerp.application.payments.BankPaymentDto;
import ua.newerp.application.payments.BankPaymentFilter;
import ua.newerp.application.payments.BankPaymentRepository;
import ua.newerp.application.payments.CandidateQuery;
import ua.newerp.domain.payments.BankPayment;
import ua.newerp.domain.payments.OrderCandidate;
import ua.newerp.infrastructure.ErpProperties;

/** The old site's PrivatBank payments dd_privatbank (structure reconstructed, see db/reconstructed.sql). */
@Repository
@Transactional(readOnly = true)
class JpaBankPaymentRepository implements BankPaymentRepository {

    private static final String PAYMENT_SQL = """
            SELECT p.payment_id, p.appcode, p.card, p.amount, p.rest, p.terminal, p.description, p.trandate, p.trantime,
                   p.order_id, CONCAT(o.firstname, ' ', o.lastname) AS customer, p.show_status
            FROM dd_privatbank p
            LEFT JOIN dd_order o ON o.order_id = p.order_id AND p.order_id <> 0
            """;

    private final EntityManager em;
    private final JdbcClient jdbc;
    private final int languageId;

    JpaBankPaymentRepository(EntityManager em, JdbcClient jdbc, ErpProperties erp) {
        this.em = em;
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public PagedResult<BankPaymentDto> list(BankPaymentFilter filter) {
        Paging paging = Paging.normalize(filter.page(),
                filter.pageSize() == null ? BankPaymentFilter.DEFAULT_PAGE_SIZE : filter.pageSize());
        var where = new StringBuilder(" WHERE p.show_status = :visible");
        Map<String, Object> params = new HashMap<>();
        params.put("visible", !filter.showHidden());
        if (filter.from() != null) {
            where.append(" AND p.trandate >= :from");
            params.put("from", filter.from());
        }
        if (filter.to() != null) {
            where.append(" AND p.trandate <= :to");
            params.put("to", filter.to());
        }
        if (filter.linked() != null) {
            where.append(filter.linked() ? " AND p.order_id <> 0" : " AND p.order_id = 0");
        }
        if (filter.orderId() != null) {
            where.append(" AND p.order_id = :orderId");
            params.put("orderId", filter.orderId());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            // LOCATE instead of LIKE: what a person typed is searched literally (% and _ are not wildcards)
            where.append(" AND (LOCATE(:search, p.description) > 0 OR LOCATE(:search, p.terminal) > 0 OR LOCATE(:search, p.amount) = 1)");
            params.put("search", filter.search().strip());
        }

        int total = jdbc.sql("SELECT COUNT(*) FROM dd_privatbank p" + where).params(params).query(Integer.class).single();
        List<BankPaymentDto> items = jdbc.sql(PAYMENT_SQL + where
                        + " ORDER BY p.trandate DESC, p.trantime DESC, p.payment_id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", paging.pageSize())
                .param("offset", paging.offset())
                .query(JpaBankPaymentRepository::payment)
                .list();
        return PagedResult.of(items, paging.page(), paging.pageSize(), total);
    }

    @Override
    public Optional<BankPayment> get(int id) {
        return Optional.ofNullable(em.find(BankPayment.class, id));
    }

    @Override
    public Optional<BankPaymentDto> getDto(int id) {
        return jdbc.sql(PAYMENT_SQL + " WHERE p.payment_id = :id").param("id", id).query(JpaBankPaymentRepository::payment).optional();
    }

    @Override
    public List<BankPaymentDto> listForOrder(int orderId) {
        return jdbc.sql(PAYMENT_SQL + " WHERE p.order_id = :id ORDER BY p.trandate, p.trantime")
                .param("id", orderId)
                .query(JpaBankPaymentRepository::payment)
                .list();
    }

    @Override
    public Set<String> existingAppCodes(Collection<String> appCodes) {
        if (appCodes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT appcode FROM dd_privatbank WHERE appcode IN (:codes)")
                .param("codes", appCodes).query(String.class).list());
    }

    @Override
    public List<OrderCandidate> findCandidates(CandidateQuery query) {
        String customer = query.customer();
        int customerOrderId = parseIntOrMinusOne(customer);
        // Unfinished orders (status 0: a cart the customer never checked out) are not suggested.
        // An empty IN () is not valid SQL, so an empty list becomes (-1), which matches nothing.
        return jdbc.sql("""
                SELECT o.order_id, o.firstname, o.lastname, o.telephone, o.total, o.prepayment, o.date_added,
                       COALESCE(s.name, '') AS status,
                       (SELECT p.payment_id FROM dd_privatbank p WHERE p.order_id = o.order_id LIMIT 1) AS linked_payment_id
                FROM dd_order o
                LEFT JOIN dd_order_status s ON s.order_status_id = o.order_status_id AND s.language_id = :languageId
                WHERE o.order_status_id <> 0 AND (
                    o.order_id IN (:ids)
                    OR (:customer IS NOT NULL AND (LOCATE(:customer, o.firstname) > 0 OR LOCATE(:customer, o.lastname) > 0
                        OR LOCATE(:customer, o.telephone) > 0 OR o.order_id = :customerOrderId))
                    OR (o.date_added >= :from AND o.date_added < :to
                        AND (o.total IN (:amounts) OR o.prepayment IN (:amounts) OR o.total - o.prepayment IN (:amounts))))
                ORDER BY o.order_id DESC
                LIMIT :limit
                """)
                .param("languageId", languageId)
                .param("ids", query.orderIds().isEmpty() ? List.of(-1) : query.orderIds())
                .param("customer", customer)
                .param("customerOrderId", customerOrderId)
                .param("from", query.from())
                .param("to", query.to())
                .param("amounts", query.amounts())
                .param("limit", query.limit())
                .query((rs, n) -> new OrderCandidate(rs.getInt("order_id"),
                        (rs.getString("firstname") + " " + rs.getString("lastname")).strip(), rs.getString("telephone"),
                        rs.getBigDecimal("total"), rs.getBigDecimal("prepayment"), rs.getTimestamp("date_added").toLocalDateTime(),
                        rs.getString("status"), rs.getObject("linked_payment_id", Integer.class)))
                .list();
    }

    @Override
    public boolean orderExists(int orderId) {
        return jdbc.sql("SELECT 1 FROM dd_order WHERE order_id = :id").param("id", orderId).query(Integer.class).optional().isPresent();
    }

    @Override
    @Transactional
    public void addAll(List<BankPayment> payments) {
        payments.forEach(em::persist);
    }

    @Override
    @Transactional
    public void save(BankPayment payment) {
        em.merge(payment);
        // The DTO is read back with SQL in the same transaction, so the change must reach the database first
        em.flush();
    }

    private static BankPaymentDto payment(ResultSet rs, int n) throws SQLException {
        String customer = rs.getString("customer");
        return new BankPaymentDto(rs.getInt("payment_id"), rs.getString("appcode"), rs.getString("card"),
                BankPayment.parseMoney(rs.getString("amount")), BankPayment.parseMoney(rs.getString("rest")), rs.getString("terminal"),
                rs.getString("description"), rs.getDate("trandate").toLocalDate().atTime(rs.getTime("trantime").toLocalTime()),
                rs.getInt("order_id"), customer == null ? null : customer.strip(), rs.getBoolean("show_status"));
    }

    private static int parseIntOrMinusOne(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
