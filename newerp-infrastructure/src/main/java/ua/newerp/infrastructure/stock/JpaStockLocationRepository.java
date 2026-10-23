package ua.newerp.infrastructure.stock;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import ua.newerp.application.stock.StockLocationRepository;
import ua.newerp.domain.stock.StockBalance;
import ua.newerp.domain.stock.StockTransfer;

/** Writes through JPA, the overview reads through plain SQL. */
@Repository
@Transactional(readOnly = true)
class JpaStockLocationRepository implements StockLocationRepository {

    private final EntityManager em;
    private final JdbcClient jdbc;

    JpaStockLocationRepository(EntityManager em, JdbcClient jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public List<StockBalance> findBalances(int itemId) {
        return em.createQuery("SELECT b FROM StockBalance b WHERE b.itemId = :itemId", StockBalance.class)
                .setParameter("itemId", itemId)
                .getResultList();
    }

    @Override
    public List<BalanceRow> listBalances() {
        return jdbc.sql("""
                SELECT l.item_id, l.warehouse_id, l.quantity
                FROM erp_stock_location l JOIN dd_stock_items i ON i.item_id = l.item_id
                WHERE i.status = 1 AND l.quantity <> 0
                """)
                .query((rs, n) -> new BalanceRow(rs.getInt("item_id"), rs.getInt("warehouse_id"), rs.getBigDecimal("quantity")))
                .list();
    }

    @Override
    public boolean holdsStock(int warehouseId) {
        return jdbc.sql("SELECT COUNT(*) FROM erp_stock_location WHERE warehouse_id = :id AND quantity <> 0")
                .param("id", warehouseId)
                .query(Integer.class).single() > 0;
    }

    @Override
    public List<StockTransfer> listTransfers(int itemId) {
        return em.createQuery("SELECT t FROM StockTransfer t WHERE t.itemId = :itemId ORDER BY t.createdAt DESC, t.id DESC",
                StockTransfer.class)
                .setParameter("itemId", itemId)
                .getResultList();
    }

    @Override
    public void add(StockBalance balance) {
        em.persist(balance);
    }

    @Override
    public void add(StockTransfer transfer) {
        em.persist(transfer);
    }
}
