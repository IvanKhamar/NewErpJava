package ua.newerp.infrastructure.reports;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ua.newerp.application.reports.ProductSalesRow;
import ua.newerp.application.reports.ReportStore;
import ua.newerp.domain.reports.SaleFact;

/** Reports straight from OpenCart's dd_order and dd_order_product. */
@Repository
class JdbcReportStore implements ReportStore {

    private final JdbcClient jdbc;

    JdbcReportStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SaleFact> sales(LocalDateTime from, LocalDateTime toExclusive, Integer statusId, int excludedStatusId) {
        // The product units of each order in one grouped subquery, not a subquery per order.
        return jdbc.sql("""
                SELECT o.date_added, o.total, COALESCE(p.quantity, 0) AS products
                FROM dd_order o
                LEFT JOIN (SELECT order_id, SUM(quantity) AS quantity FROM dd_order_product GROUP BY order_id) p ON p.order_id = o.order_id
                WHERE""" + orders())
                .params(params(from, toExclusive, statusId, excludedStatusId))
                .query((rs, n) -> new SaleFact(rs.getObject("date_added", LocalDateTime.class), rs.getBigDecimal("total"), rs.getInt("products")))
                .list();
    }

    @Override
    public List<ProductSalesRow> products(LocalDateTime from, LocalDateTime toExclusive, Integer statusId, int excludedStatusId, int limit) {
        Map<String, Object> params = params(from, toExclusive, statusId, excludedStatusId);
        params.put("limit", limit);
        // Name and model come from the newest order line of the product (it may have been renamed).
        return jdbc.sql("""
                SELECT s.product_id, COALESCE(n.name, '') AS name, COALESCE(n.model, '') AS model, s.quantity, s.total
                FROM (SELECT op.product_id, SUM(op.quantity) AS quantity, SUM(op.total) AS total
                      FROM dd_order_product op JOIN dd_order o ON o.order_id = op.order_id
                      WHERE""" + orders() + """

                      GROUP BY op.product_id
                      ORDER BY quantity DESC, total DESC, op.product_id
                      LIMIT :limit) s
                LEFT JOIN dd_order_product n ON n.order_product_id =
                    (SELECT MAX(order_product_id) FROM dd_order_product WHERE product_id = s.product_id)
                ORDER BY s.quantity DESC, s.total DESC, s.product_id
                """)
                .params(params)
                .query((rs, n) -> new ProductSalesRow(rs.getInt("product_id"), rs.getString("name"), rs.getString("model"), rs.getInt("quantity"),
                        rs.getBigDecimal("total")))
                .list();
    }

    @Override
    public int newCustomers(LocalDateTime from, LocalDateTime toExclusive) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_customer WHERE date_added >= :from AND date_added < :to")
                .param("from", from).param("to", toExclusive)
                .query(Integer.class).single();
    }

    private static String orders() {
        return " o.date_added >= :from AND o.date_added < :to"
                + " AND (CASE WHEN :statusId IS NULL THEN o.order_status_id > 0 AND o.order_status_id <> :excluded"
                + " ELSE o.order_status_id = :statusId END)";
    }

    private static Map<String, Object> params(LocalDateTime from, LocalDateTime toExclusive, Integer statusId, int excludedStatusId) {
        Map<String, Object> params = new HashMap<>();
        params.put("from", from);
        params.put("to", toExclusive);
        params.put("statusId", statusId);
        params.put("excluded", excludedStatusId);
        return params;
    }
}
