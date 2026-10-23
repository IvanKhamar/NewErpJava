package ua.newerp.infrastructure.stock;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.stock.StockReportSource;
import ua.newerp.domain.production.ProductionLogEntry;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.domain.stock.StockMovement;
import ua.newerp.domain.stock.Stocktaking;

/**
 * Report totals straight from the two movement logs. What a row means is told by its action (stage and direction) and by the
 * comment the code writes: "Інвентаризація…", "Замовлення #…", "Скасування #…".
 */
@Repository
@Transactional(readOnly = true)
class JdbcStockReportSource implements StockReportSource {

    private final JdbcClient jdbc;

    JdbcStockReportSource(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ItemFlow> itemFlows(LocalDateTime since) {
        return jdbc.sql("""
                SELECT item_id,
                       SUM(CASE WHEN comment LIKE :counted THEN 0 WHEN action = :use THEN quantity ELSE 0 END) AS used,
                       SUM(CASE WHEN comment LIKE :counted THEN 0 WHEN action = :use THEN 0 ELSE quantity END) AS received,
                       SUM(CASE WHEN comment NOT LIKE :counted THEN 0 WHEN action = :use THEN -quantity ELSE quantity END) AS corrected
                FROM dd_stock_log
                WHERE date_added >= :since
                GROUP BY item_id
                """)
                .param("counted", Stocktaking.COMMENT + "%")
                .param("use", StockMovement.USE)
                .param("since", since)
                .query((rs, n) -> new ItemFlow(rs.getInt("item_id"), rs.getBigDecimal("used"), rs.getBigDecimal("received"),
                        rs.getBigDecimal("corrected")))
                .list();
    }

    @Override
    public List<SizeFlow> sizeFlows(LocalDateTime since) {
        // The current names win over the ones copied into the log, so a renamed size is not split in two
        return jdbc.sql("""
                SELECT l.production_id, l.size_id, MAX(p.name) AS model_name, MAX(COALESCE(s.size_name, l.size_name)) AS size_name,
                       SUM(CASE WHEN l.comment LIKE :counted THEN 0
                                WHEN l.stage = :cut AND l.direction = :in THEN l.quantity ELSE 0 END) AS cut,
                       SUM(CASE WHEN l.comment LIKE :counted OR l.comment LIKE :cancelled THEN 0
                                WHEN l.stage = :sewn AND l.direction = :in THEN l.quantity ELSE 0 END) AS sewn,
                       SUM(CASE WHEN l.stage <> :sewn THEN 0
                                WHEN l.direction = :out AND l.comment LIKE :ordered THEN l.quantity
                                WHEN l.direction = :in AND l.comment LIKE :cancelled THEN -l.quantity ELSE 0 END) AS sold,
                       SUM(CASE WHEN l.comment LIKE :counted OR l.comment LIKE :ordered THEN 0
                                WHEN l.stage = :sewn AND l.direction = :out THEN l.quantity ELSE 0 END) AS shipped,
                       SUM(CASE WHEN l.comment NOT LIKE :counted THEN 0
                                WHEN l.direction = :in THEN l.quantity ELSE -l.quantity END) AS corrected
                FROM dd_stock_production_log l
                JOIN dd_stock_production p ON p.production_id = l.production_id
                LEFT JOIN dd_stock_production_sizes s ON s.size_id = l.size_id
                WHERE l.date_added >= :since
                GROUP BY l.production_id, l.size_id
                ORDER BY MAX(p.sort_order), MAX(p.name), MAX(s.sort_order), l.size_id
                """)
                .param("counted", Stocktaking.COMMENT + "%")
                // "Замовлення #0" → "Замовлення #%": the text the domain writes, with any order number
                .param("ordered", ProductionModel.orderComment(0).replace("0", "%"))
                .param("cancelled", ProductionModel.cancelComment(0).replace("0", "%"))
                .param("cut", ProductionLogEntry.CUT)
                .param("sewn", ProductionLogEntry.SEWN)
                .param("in", ProductionLogEntry.IN)
                .param("out", ProductionLogEntry.OUT)
                .param("since", since)
                .query((rs, n) -> new SizeFlow(rs.getInt("production_id"), rs.getString("model_name"), rs.getInt("size_id"),
                        rs.getString("size_name"), rs.getBigDecimal("cut"), rs.getBigDecimal("sewn"), rs.getBigDecimal("sold"),
                        rs.getBigDecimal("shipped"), rs.getBigDecimal("corrected")))
                .list();
    }
}
