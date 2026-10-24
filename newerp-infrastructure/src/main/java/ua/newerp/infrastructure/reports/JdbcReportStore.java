package ua.newerp.infrastructure.reports;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ua.newerp.application.reports.ProductSalesRow;
import ua.newerp.application.reports.ReportStore;
import ua.newerp.application.reports.StatusReportDto;
import ua.newerp.domain.reports.SaleFact;
import ua.newerp.infrastructure.ErpProperties;

/** Reports straight from OpenCart's dd_order, dd_order_product and dd_return. */
@Repository
class JdbcReportStore implements ReportStore {

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcReportStore(JdbcClient jdbc, ErpProperties properties) {
        this.jdbc = jdbc;
        this.languageId = properties.languageId();
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

    @Override
    public List<StatusReportDto.OrderRow> ordersByStatus(LocalDateTime from, LocalDateTime toExclusive) {
        // Grouped first, then named: a status missing from dd_order_status still shows, as its number.
        return jdbc.sql("""
                SELECT g.order_status_id, COALESCE(s.name, CONCAT('#', g.order_status_id)) AS name, g.orders, g.total
                FROM (SELECT order_status_id, COUNT(*) AS orders, SUM(total) AS total
                      FROM dd_order
                      WHERE date_added >= :from AND date_added < :to AND order_status_id > 0
                      GROUP BY order_status_id) g
                LEFT JOIN dd_order_status s ON s.order_status_id = g.order_status_id AND s.language_id = :lang
                ORDER BY g.order_status_id
                """)
                .param("from", from).param("to", toExclusive).param("lang", languageId)
                .query((rs, n) -> new StatusReportDto.OrderRow(rs.getInt("order_status_id"), rs.getString("name"), rs.getInt("orders"),
                        rs.getBigDecimal("total")))
                .list();
    }

    @Override
    public List<StatusReportDto.ReturnRow> returnsByStatus(LocalDateTime from, LocalDateTime toExclusive) {
        return jdbc.sql("""
                SELECT g.return_status_id, COALESCE(s.name, CONCAT('#', g.return_status_id)) AS name, g.returns, g.quantity
                FROM (SELECT return_status_id, COUNT(*) AS returns, SUM(quantity) AS quantity
                      FROM dd_return
                      WHERE date_added >= :from AND date_added < :to
                      GROUP BY return_status_id) g
                LEFT JOIN dd_return_status s ON s.return_status_id = g.return_status_id AND s.language_id = :lang
                ORDER BY g.return_status_id
                """)
                .param("from", from).param("to", toExclusive).param("lang", languageId)
                .query((rs, n) -> new StatusReportDto.ReturnRow(rs.getInt("return_status_id"), rs.getString("name"), rs.getInt("returns"),
                        rs.getInt("quantity")))
                .list();
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
