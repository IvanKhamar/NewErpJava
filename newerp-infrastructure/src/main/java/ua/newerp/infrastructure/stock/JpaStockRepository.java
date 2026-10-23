package ua.newerp.infrastructure.stock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockItemFilter;
import ua.newerp.application.stock.StockMovementDto;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StockSupplierDto;
import ua.newerp.domain.stock.PurchaseOrder;
import ua.newerp.domain.stock.StockCategory;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;
import ua.newerp.domain.stock.StockSupplier;

/**
 * Writes through JPA (EntityManager = DbContext: find, persist, change tracking), reads through plain SQL.
 */
@Repository
@Transactional(readOnly = true)
class JpaStockRepository implements StockRepository {

    // Category and supplier are optional (NULL in the old module), so both joins are LEFT JOIN.
    private static final String ITEM_SQL = """
            SELECT i.item_id, i.name, i.unit, i.category_id, c.name AS category_name, i.supplier_id,
                   s.name AS supplier_name, i.stock, i.min_stock, i.used_for, i.price
            FROM dd_stock_items i
            LEFT JOIN dd_stock_categories c ON c.category_id = i.category_id
            LEFT JOIN dd_stock_suppliers s ON s.supplier_id = i.supplier_id
            """;

    private final EntityManager em;
    private final JdbcClient jdbc;

    JpaStockRepository(EntityManager em, JdbcClient jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public List<LookupItem> listCategories() {
        return jdbc.sql("SELECT category_id, name FROM dd_stock_categories ORDER BY sort_order, name")
                .query((rs, n) -> new LookupItem(rs.getInt("category_id"), rs.getString("name")))
                .list();
    }

    @Override
    public List<StockSupplierDto> listSuppliers() {
        return jdbc.sql("SELECT supplier_id, name, phone, email, categories, notes FROM dd_stock_suppliers ORDER BY name")
                .query((rs, n) -> new StockSupplierDto(rs.getInt("supplier_id"), rs.getString("name"), rs.getString("phone"),
                        rs.getString("email"), rs.getString("categories"), rs.getString("notes")))
                .list();
    }

    @Override
    public List<StockItemDto> listItems(StockItemFilter filter) {
        StringBuilder sql = new StringBuilder(ITEM_SQL).append(" WHERE i.status = 1");
        Map<String, Object> params = new HashMap<>();
        if (filter.categoryId() != null) {
            sql.append(" AND i.category_id = :categoryId");
            params.put("categoryId", filter.categoryId());
        }
        if (filter.onlyBelowMinimum()) {
            sql.append(" AND i.stock < i.min_stock");
        }
        sql.append(" ORDER BY i.name");
        return jdbc.sql(sql.toString()).params(params).query(JpaStockRepository::item).list();
    }

    @Override
    public Optional<StockItemDto> getItemDto(int itemId) {
        return jdbc.sql(ITEM_SQL + " WHERE i.item_id = :id").param("id", itemId).query(JpaStockRepository::item).optional();
    }

    @Override
    public List<StockMovementDto> listMovements(int itemId) {
        return jdbc.sql("""
                SELECT log_id, item_id, action, quantity, stock_before, stock_after, comment, user_id, username, date_added
                FROM dd_stock_log WHERE item_id = :id ORDER BY date_added DESC, log_id DESC
                """)
                .param("id", itemId)
                .query((rs, n) -> new StockMovementDto(
                        rs.getInt("log_id"),
                        rs.getInt("item_id"),
                        StockMovement.USE.equals(rs.getString("action")) ? rs.getBigDecimal("quantity").negate() : rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("stock_before"),
                        rs.getBigDecimal("stock_after"),
                        rs.getString("comment"),
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getTimestamp("date_added").toLocalDateTime()))
                .list();
    }

    @Override
    public boolean categoryExists(int categoryId) {
        return exists("SELECT COUNT(*) FROM dd_stock_categories WHERE category_id = :v", categoryId);
    }

    @Override
    public boolean categoryNameTaken(String name, Integer exceptId) {
        em.flush();
        return jdbc.sql("SELECT COUNT(*) FROM dd_stock_categories WHERE name = :name AND category_id <> :except")
                .param("name", name)
                .param("except", exceptId == null ? 0 : exceptId)
                .query(Integer.class).single() > 0;
    }

    @Override
    public Optional<StockCategory> findCategory(int categoryId) {
        return Optional.ofNullable(em.find(StockCategory.class, categoryId));
    }

    @Override
    public Optional<StockSupplier> findSupplier(int supplierId) {
        return Optional.ofNullable(em.find(StockSupplier.class, supplierId));
    }

    @Override
    public int countItemsInCategory(int categoryId) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_stock_items WHERE category_id = :v").param("v", categoryId).query(Integer.class).single();
    }

    @Override
    public int countSupplierReferences(int supplierId) {
        return jdbc.sql("""
                SELECT (SELECT COUNT(*) FROM dd_stock_items WHERE supplier_id = :v)
                     + (SELECT COUNT(*) FROM erp_purchase_order WHERE supplier_id = :v)
                """).param("v", supplierId).query(Integer.class).single();
    }

    @Override
    public List<String> modelsUsingItem(int itemId) {
        return jdbc.sql("""
                SELECT DISTINCT p.name FROM dd_stock_production p
                WHERE p.status = 1 AND (
                    EXISTS (SELECT 1 FROM dd_stock_production_components c
                            JOIN dd_stock_production_component_bom b ON b.component_id = c.component_id
                            WHERE c.production_id = p.production_id AND b.item_id = :v)
                    OR EXISTS (SELECT 1 FROM dd_stock_production_sewn_bom s WHERE s.production_id = p.production_id AND s.item_id = :v))
                ORDER BY p.name
                """).param("v", itemId).query(String.class).list();
    }

    @Override
    public List<Integer> openPurchaseOrdersWithItem(int itemId) {
        return jdbc.sql("""
                SELECT DISTINCT o.id FROM erp_purchase_order o JOIN erp_purchase_order_line l ON l.order_id = o.id
                WHERE l.item_id = :v AND o.status IN (:open) ORDER BY o.id
                """).param("v", itemId).param("open", List.of(PurchaseOrder.DRAFT, PurchaseOrder.ORDERED))
                .query(Integer.class).list();
    }

    @Override
    public void remove(StockCategory category) {
        em.remove(category);
    }

    @Override
    public void remove(StockSupplier supplier) {
        em.remove(supplier);
    }

    @Override
    public boolean supplierExists(int supplierId) {
        return exists("SELECT COUNT(*) FROM dd_stock_suppliers WHERE supplier_id = :v", supplierId);
    }

    @Override
    public Optional<StockItem> findItem(int itemId) {
        // find = DbSet.Find: the row as a tracked object; changes are written at commit.
        // PESSIMISTIC_WRITE = SELECT ... FOR UPDATE: whoever changes the same item at the same time (here or on the
        // old site) waits until this transaction ends, so no change of the quantity is lost.
        return Optional.ofNullable(em.find(StockItem.class, itemId, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public void add(StockCategory category) {
        em.persist(category);
    }

    @Override
    public void add(StockSupplier supplier) {
        em.persist(supplier);
    }

    @Override
    public void add(StockItem item) {
        em.persist(item);
    }

    @Override
    public void add(StockMovement movement) {
        em.persist(movement);
    }

    private boolean exists(String sql, Object value) {
        // Pending JPA changes go to the database first, so this SQL sees them
        em.flush();
        return jdbc.sql(sql).param("v", value).query(Integer.class).single() > 0;
    }

    static StockItemDto item(ResultSet rs, int rowNum) throws SQLException {
        var quantity = rs.getBigDecimal("stock");
        var min = rs.getBigDecimal("min_stock");
        return new StockItemDto(rs.getInt("item_id"), rs.getString("name"), rs.getString("unit"),
                rs.getObject("category_id", Integer.class), rs.getString("category_name"),
                rs.getObject("supplier_id", Integer.class), rs.getString("supplier_name"),
                quantity, min, quantity.compareTo(min) < 0, rs.getString("used_for"), rs.getBigDecimal("price"));
    }
}
