package ua.newerp.infrastructure.production;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import ua.newerp.application.production.SewnLocationRepository;
import ua.newerp.domain.production.SewnBalance;
import ua.newerp.domain.production.SewnTransfer;

/** Writes through JPA, the overview reads through plain SQL. */
@Repository
@Transactional(readOnly = true)
class JpaSewnLocationRepository implements SewnLocationRepository {

    private final EntityManager em;
    private final JdbcClient jdbc;

    JpaSewnLocationRepository(EntityManager em, JdbcClient jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public List<SizeRow> listActiveSizes() {
        return jdbc.sql("""
                SELECT p.production_id, p.name, s.size_id, s.size_name, s.sewn_stock
                FROM dd_stock_production p JOIN dd_stock_production_sizes s ON s.production_id = p.production_id
                WHERE p.status = 1
                ORDER BY p.sort_order, p.name, s.sort_order, s.size_id
                """)
                .query((rs, n) -> new SizeRow(rs.getInt("production_id"), rs.getString("name"), rs.getInt("size_id"),
                        rs.getString("size_name"), rs.getBigDecimal("sewn_stock")))
                .list();
    }

    @Override
    public List<SewnBalance> findBalances(int productionId) {
        // JPQL over the mapped classes: the balances of the model's sizes (the size row is not mapped to the balance)
        return em.createQuery("""
                SELECT b FROM SewnBalance b WHERE b.sizeId IN (SELECT s.id FROM ProductionSize s WHERE s.productionId = :id)
                """, SewnBalance.class)
                .setParameter("id", productionId)
                .getResultList();
    }

    @Override
    public List<BalanceRow> listBalances() {
        return jdbc.sql("""
                SELECT s.production_id, l.size_id, l.warehouse_id, l.quantity
                FROM erp_production_location l
                JOIN dd_stock_production_sizes s ON s.size_id = l.size_id
                JOIN dd_stock_production p ON p.production_id = s.production_id
                WHERE p.status = 1 AND l.quantity <> 0
                """)
                .query((rs, n) -> new BalanceRow(rs.getInt("production_id"), rs.getInt("size_id"), rs.getInt("warehouse_id"),
                        rs.getBigDecimal("quantity")))
                .list();
    }

    @Override
    public boolean holdsStock(int warehouseId) {
        return jdbc.sql("SELECT COUNT(*) FROM erp_production_location WHERE warehouse_id = :id AND quantity <> 0")
                .param("id", warehouseId)
                .query(Integer.class).single() > 0;
    }

    @Override
    public List<SewnTransfer> listTransfers(int productionId) {
        return em.createQuery("SELECT t FROM SewnTransfer t WHERE t.productionId = :id ORDER BY t.createdAt DESC, t.id DESC",
                SewnTransfer.class)
                .setParameter("id", productionId)
                .getResultList();
    }

    @Override
    public void add(SewnBalance balance) {
        em.persist(balance);
    }

    @Override
    public void add(SewnTransfer transfer) {
        em.persist(transfer);
    }
}
