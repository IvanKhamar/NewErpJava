package ua.newerp.infrastructure.cashbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import ua.newerp.application.cashbox.CashboxRepository;
import ua.newerp.application.cashbox.ChequeDto;
import ua.newerp.application.cashbox.ChequeFilter;
import ua.newerp.application.cashbox.OrderForCheque;
import ua.newerp.application.cashbox.WorkShiftDto;
import ua.newerp.application.cashbox.WorkShiftFilter;
import ua.newerp.application.cashbox.ZReportDto;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;
import ua.newerp.domain.cashbox.CashboxDirectoryEntry;
import ua.newerp.domain.cashbox.CashboxRequisites;
import ua.newerp.domain.cashbox.CashboxWarehouse;
import ua.newerp.domain.cashbox.Cashier;
import ua.newerp.domain.cashbox.Cheque;
import ua.newerp.domain.cashbox.ReceiptSourceLine;
import ua.newerp.domain.cashbox.WorkShift;
import ua.newerp.domain.cashbox.ZReport;

/**
 * The old site's Checkbox tables dd_cashbox_* (structure reconstructed, see db/reconstructed.sql). The columns with cashier keys
 * (login, password, checkbox_key, access_token) are deliberately not mapped: NewErp neither reads nor writes them.
 */
@Repository
@Transactional(readOnly = true)
class JpaCashboxRepository implements CashboxRepository {

    private static final String CHEQUE_SQL = """
            SELECT c.cheque_id, c.order_id, CONCAT(o.firstname, ' ', o.lastname) AS customer, c.cashbox_user_id, k.name AS cashier,
                   w.name AS warehouse, r.name AS requisites, c.sum, c.checkbox_id, c.cashbox_work_shift_id, c.cashbox_report_id,
                   c.date_added
            FROM dd_cashbox_cheque c
            LEFT JOIN dd_order o ON o.order_id = c.order_id
            LEFT JOIN dd_cashbox_user k ON k.user_id = c.cashbox_user_id
            LEFT JOIN dd_cashbox_warehouse w ON w.warehouse_id = c.cashbox_warehouse_id
            LEFT JOIN dd_cashbox_requisites r ON r.requisites_id = c.cashbox_requisites_id
            """;

    private final EntityManager em;
    private final JdbcClient jdbc;

    JpaCashboxRepository(EntityManager em, JdbcClient jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public List<Cashier> listCashiers() {
        return em.createQuery("SELECT c FROM Cashier c", Cashier.class).getResultList();
    }

    @Override
    public Optional<Cashier> getCashier(int id) {
        return Optional.ofNullable(em.find(Cashier.class, id));
    }

    @Override
    public List<CashboxWarehouse> listWarehouses() {
        return em.createQuery("SELECT w FROM CashboxWarehouse w ORDER BY w.id", CashboxWarehouse.class).getResultList();
    }

    @Override
    public Optional<CashboxWarehouse> getWarehouse(int id) {
        return Optional.ofNullable(em.find(CashboxWarehouse.class, id));
    }

    @Override
    public List<CashboxRequisites> listRequisites() {
        return em.createQuery("SELECT r FROM CashboxRequisites r ORDER BY r.id", CashboxRequisites.class).getResultList();
    }

    @Override
    public Optional<CashboxRequisites> getRequisites(int id) {
        return Optional.ofNullable(em.find(CashboxRequisites.class, id));
    }

    @Override
    public boolean warehouseInUse(int id) {
        return exists("SELECT 1 FROM dd_cashbox_user WHERE warehouse_id = :id UNION ALL "
                + "SELECT 1 FROM dd_cashbox_cheque WHERE cashbox_warehouse_id = :id LIMIT 1", id);
    }

    @Override
    public boolean requisitesInUse(int id) {
        return exists("SELECT 1 FROM dd_cashbox_user WHERE requisites_id = :id UNION ALL "
                + "SELECT 1 FROM dd_cashbox_cheque WHERE cashbox_requisites_id = :id LIMIT 1", id);
    }

    /**
     * The entities come here detached (the service has no transaction), so a changed one is merged back.
     * A new one (no id yet) is persisted: with IDENTITY the INSERT runs right away and the id is set on the object.
     */
    @Override
    @Transactional
    public <T> T save(T entity) {
        if (em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity) == null) {
            em.persist(entity);
            return entity;
        }
        em.merge(entity);
        return entity;
    }

    @Override
    @Transactional
    public void remove(CashboxDirectoryEntry entry) {
        em.remove(em.find(entry.getClass(), entry.getId()));
    }

    @Override
    public List<WorkShift> listOpenShifts() {
        return em.createQuery("SELECT s FROM WorkShift s WHERE s.dateEnd IS NULL", WorkShift.class).getResultList();
    }

    @Override
    public Optional<WorkShift> getShift(int id) {
        return Optional.ofNullable(em.find(WorkShift.class, id));
    }

    @Override
    public List<WorkShiftDto> listShifts(WorkShiftFilter filter) {
        var where = new StringBuilder(" WHERE 1 = 1");
        var params = new HashMap<String, Object>();
        if (filter.cashierId() != null) {
            where.append(" AND s.cashbox_user_id = :cashierId");
            params.put("cashierId", filter.cashierId());
        }
        period(where, params, "s.date_start", filter.from(), filter.to());

        // Count and sum of fiscalized receipts of each shift
        return jdbc.sql("""
                SELECT s.work_shift_id, s.cashbox_user_id, COALESCE(c.name, '') AS cashier, s.checkbox_shift_id,
                       TRIM(CONCAT(u.firstname, ' ', u.lastname)) AS opened_by, s.date_start, s.date_end,
                       (SELECT COUNT(*) FROM dd_cashbox_cheque ch WHERE ch.cashbox_work_shift_id = s.work_shift_id AND ch.checkbox_id <> '') AS cheques,
                       (SELECT COALESCE(SUM(ch.sum), 0) FROM dd_cashbox_cheque ch
                        WHERE ch.cashbox_work_shift_id = s.work_shift_id AND ch.checkbox_id <> '') AS cheques_sum
                FROM dd_cashbox_work_shift s
                LEFT JOIN dd_cashbox_user c ON c.user_id = s.cashbox_user_id
                LEFT JOIN dd_user u ON u.user_id = s.user_id
                """ + where + " ORDER BY s.work_shift_id DESC LIMIT 500")
                .params(params)
                .query((rs, n) -> new WorkShiftDto(rs.getInt("work_shift_id"), rs.getInt("cashbox_user_id"), rs.getString("cashier"),
                        rs.getString("checkbox_shift_id"), rs.getString("opened_by"), dateTime(rs, "date_start"),
                        dateTime(rs, "date_end"), rs.getInt("cheques"), rs.getBigDecimal("cheques_sum")))
                .list();
    }

    @Override
    @Transactional
    public void saveReport(ZReport report, List<WorkShift> closedShifts) {
        em.persist(report);
        closedShifts.forEach(em::merge);
    }

    @Override
    public List<ZReportDto> listReports(Integer cashierId, LocalDate from, LocalDate to) {
        var where = new StringBuilder(" WHERE 1 = 1");
        var params = new HashMap<String, Object>();
        if (cashierId != null) {
            where.append(" AND r.cashbox_user_id = :cashierId");
            params.put("cashierId", cashierId);
        }
        period(where, params, "r.date_added", from, to);

        return jdbc.sql("""
                SELECT r.report_id, r.cashbox_user_id, COALESCE(c.name, '') AS cashier, r.z_report_id,
                       TRIM(CONCAT(u.firstname, ' ', u.lastname)) AS closed_by, r.date_added
                FROM dd_cashbox_report r
                LEFT JOIN dd_cashbox_user c ON c.user_id = r.cashbox_user_id
                LEFT JOIN dd_user u ON u.user_id = r.user_id
                """ + where + " ORDER BY r.report_id DESC LIMIT 500")
                .params(params)
                .query((rs, n) -> new ZReportDto(rs.getInt("report_id"), rs.getInt("cashbox_user_id"), rs.getString("cashier"),
                        rs.getString("z_report_id"), rs.getString("closed_by"), dateTime(rs, "date_added")))
                .list();
    }

    @Override
    public List<Cheque> listUnreportedCheques(int cashierId, int shiftId, LocalDateTime since) {
        return em.createQuery("""
                SELECT c FROM Cheque c
                WHERE c.cashierId = :cashierId AND c.reportId = 0
                  AND (c.workShiftId = :shiftId OR (c.workShiftId = 0 AND c.dateAdded >= :since))
                """, Cheque.class)
                .setParameter("cashierId", cashierId)
                .setParameter("shiftId", shiftId)
                .setParameter("since", since)
                .getResultList();
    }

    @Override
    @Transactional
    public void attachChequesToReport(List<Integer> chequeIds, int reportId) {
        if (chequeIds.isEmpty()) {
            return;
        }
        jdbc.sql("UPDATE dd_cashbox_cheque SET cashbox_report_id = :reportId WHERE cheque_id IN (:ids)")
                .param("reportId", reportId)
                .param("ids", chequeIds)
                .update();
    }

    @Override
    public Optional<Cheque> getCheque(int id) {
        return Optional.ofNullable(em.find(Cheque.class, id));
    }

    /** One receipt per order; if there are several (old site), the latest. */
    @Override
    public Optional<Cheque> getChequeForOrder(int orderId) {
        return em.createQuery("SELECT c FROM Cheque c WHERE c.orderId = :orderId ORDER BY c.id DESC", Cheque.class)
                .setParameter("orderId", orderId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<ChequeDto> getChequeDto(int id) {
        return jdbc.sql(CHEQUE_SQL + " WHERE c.cheque_id = :id").param("id", id).query(JpaCashboxRepository::cheque).optional();
    }

    @Override
    public PagedResult<ChequeDto> listCheques(ChequeFilter filter) {
        Paging paging = Paging.normalize(filter.page(), filter.pageSize() == null ? ChequeFilter.DEFAULT_PAGE_SIZE : filter.pageSize());
        var where = new StringBuilder(" WHERE 1 = 1");
        var params = new HashMap<String, Object>();
        if (filter.cashierId() != null) {
            where.append(" AND c.cashbox_user_id = :cashierId");
            params.put("cashierId", filter.cashierId());
        }
        if (filter.orderId() != null) {
            where.append(" AND c.order_id = :orderId");
            params.put("orderId", filter.orderId());
        }
        period(where, params, "c.date_added", filter.from(), filter.to());
        if (filter.fiscalized() != null) {
            where.append(filter.fiscalized() ? " AND c.checkbox_id <> ''" : " AND c.checkbox_id = ''");
        }

        int total = jdbc.sql("SELECT COUNT(*) FROM dd_cashbox_cheque c" + where).params(params).query(Integer.class).single();
        List<ChequeDto> items = jdbc.sql(CHEQUE_SQL + where + " ORDER BY c.cheque_id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", paging.pageSize())
                .param("offset", paging.offset())
                .query(JpaCashboxRepository::cheque)
                .list();
        return PagedResult.of(items, paging.page(), paging.pageSize(), total);
    }

    @Override
    @Transactional
    public void deleteCheque(int id) {
        jdbc.sql("DELETE FROM dd_cashbox_cheque WHERE cheque_id = :id").param("id", id).update();
    }

    @Override
    public Optional<OrderForCheque> getOrderForCheque(int orderId) {
        // The name from the order line (as the customer saw it), not the current catalog name the old site took.
        List<ReceiptSourceLine> lines = jdbc.sql("""
                SELECT product_id, name, price, quantity FROM dd_order_product WHERE order_id = :id ORDER BY order_product_id
                """)
                .param("id", orderId)
                .query((rs, n) -> new ReceiptSourceLine(rs.getInt("product_id"), rs.getString("name"), rs.getBigDecimal("price"),
                        rs.getInt("quantity")))
                .list();
        return jdbc.sql("SELECT order_id, total, prepayment, payment_code, payment_method, email FROM dd_order WHERE order_id = :id")
                .param("id", orderId)
                .query((rs, n) -> new OrderForCheque(rs.getInt("order_id"), rs.getBigDecimal("total"), rs.getBigDecimal("prepayment"),
                        rs.getString("payment_code"), rs.getString("payment_method"), rs.getString("email"), new ArrayList<>(lines)))
                .optional();
    }

    private boolean exists(String sql, int id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).optional().isPresent();
    }

    /** from and to are whole days: to includes the whole last day. */
    private static void period(StringBuilder where, Map<String, Object> params, String column, LocalDate from, LocalDate to) {
        if (from != null) {
            where.append(" AND ").append(column).append(" >= :from");
            params.put("from", from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND ").append(column).append(" < :to");
            params.put("to", to.plusDays(1).atStartOfDay());
        }
    }

    private static ChequeDto cheque(ResultSet rs, int n) throws SQLException {
        String customer = rs.getString("customer");
        String checkboxId = rs.getString("checkbox_id");
        return new ChequeDto(rs.getInt("cheque_id"), rs.getInt("order_id"), customer == null ? null : customer.strip(),
                rs.getInt("cashbox_user_id"), rs.getString("cashier"), rs.getString("warehouse"), rs.getString("requisites"),
                rs.getBigDecimal("sum"), checkboxId, !checkboxId.isEmpty(), null, rs.getInt("cashbox_work_shift_id"),
                rs.getInt("cashbox_report_id"), dateTime(rs, "date_added"));
    }

    private static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
