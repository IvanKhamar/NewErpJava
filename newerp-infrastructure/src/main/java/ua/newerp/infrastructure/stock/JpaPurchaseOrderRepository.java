package ua.newerp.infrastructure.stock;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import ua.newerp.application.stock.PurchaseOrderDto;
import ua.newerp.application.stock.PurchaseOrderFilter;
import ua.newerp.application.stock.PurchaseOrderLineDto;
import ua.newerp.application.stock.PurchaseOrderRepository;
import ua.newerp.application.stock.PurchaseOrderSummaryDto;
import ua.newerp.application.stock.ReorderLineDto;
import ua.newerp.application.stock.ReorderService;
import ua.newerp.domain.stock.PurchaseOrder;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;

@Repository
@Transactional(readOnly = true)
class JpaPurchaseOrderRepository implements PurchaseOrderRepository {

    private final EntityManager em;
    private final JdbcClient jdbc;

    JpaPurchaseOrderRepository(EntityManager em, JdbcClient jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public List<PurchaseOrderSummaryDto> list(PurchaseOrderFilter filter) {
        String where = filter.status() == null || filter.status().isEmpty() ? "" : " WHERE o.status = :status";
        Map<String, Object> params = new HashMap<>();
        if (!where.isEmpty()) {
            params.put("status", filter.status());
        }
        return jdbc.sql("""
                SELECT o.id, o.supplier_id, s.name AS supplier_name, o.status, o.created_at,
                       (SELECT COUNT(*) FROM erp_purchase_order_line l WHERE l.order_id = o.id) AS line_count,
                       (SELECT COALESCE(SUM(l.quantity * l.price), 0) FROM erp_purchase_order_line l WHERE l.order_id = o.id) AS total
                FROM erp_purchase_order o
                LEFT JOIN dd_stock_suppliers s ON s.supplier_id = o.supplier_id
                """ + where + " ORDER BY o.created_at DESC, o.id DESC")
                .params(params)
                .query((rs, n) -> new PurchaseOrderSummaryDto(rs.getInt("id"), rs.getInt("supplier_id"),
                        rs.getString("supplier_name"), rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getInt("line_count"), rs.getBigDecimal("total")))
                .list();
    }

    @Override
    public Optional<PurchaseOrderDto> getDto(int id) {
        PurchaseOrder order = em.find(PurchaseOrder.class, id);
        if (order == null) {
            return Optional.empty();
        }
        record Supplier(String name, String phone) {
        }
        Supplier supplier = jdbc.sql("SELECT name, phone FROM dd_stock_suppliers WHERE supplier_id = :id")
                .param("id", order.getSupplierId())
                .query((rs, n) -> new Supplier(rs.getString("name"), rs.getString("phone")))
                .optional().orElse(null);
        record Item(String name, String unit) {
        }
        Map<Integer, Item> items = new HashMap<>();
        List<Integer> itemIds = order.getLines().stream().map(l -> l.getItemId()).toList();
        if (!itemIds.isEmpty()) {
            jdbc.sql("SELECT item_id, name, unit FROM dd_stock_items WHERE item_id IN (:ids)")
                    .param("ids", itemIds)
                    .query(rs -> {
                        items.put(rs.getInt("item_id"), new Item(rs.getString("name"), rs.getString("unit")));
                    });
        }

        Collator ukrainian = Collator.getInstance(Locale.of("uk", "UA"));
        List<PurchaseOrderLineDto> lines = order.getLines().stream()
                .map(l -> {
                    Item item = items.get(l.getItemId());
                    return new PurchaseOrderLineDto(l.getId(), l.getItemId(), item == null ? null : item.name(),
                            item == null ? null : item.unit(), l.getQuantity(), l.getPrice(), l.getQuantity().multiply(l.getPrice()),
                            l.getReceivedQuantity(), l.getRemaining());
                })
                .sorted(Comparator.comparing(PurchaseOrderLineDto::itemName, Comparator.nullsFirst(ukrainian)))
                .toList();
        String message = ReorderService.messageText(lines.stream()
                .map(l -> new ReorderLineDto(l.itemId(), l.itemName() == null ? "позиція " + l.itemId() : l.itemName(),
                        l.unit() == null ? "" : l.unit(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, l.quantity(),
                        l.price(), l.sum()))
                .toList());

        return Optional.of(new PurchaseOrderDto(order.getId(), order.getSupplierId(),
                supplier == null ? null : supplier.name(), supplier == null ? null : supplier.phone(), order.getStatus(),
                order.getComment(), order.getCreatedAt(), order.getOrderedAt(), order.getClosedAt(), lines,
                lines.stream().map(PurchaseOrderLineDto::sum).reduce(BigDecimal.ZERO, BigDecimal::add), message));
    }

    @Override
    public Optional<PurchaseOrder> find(int id) {
        return Optional.ofNullable(em.find(PurchaseOrder.class, id));
    }

    @Override
    public Map<Integer, BigDecimal> onOrder() {
        Map<Integer, BigDecimal> result = new HashMap<>();
        jdbc.sql("""
                SELECT l.item_id, SUM(GREATEST(0, l.quantity - l.received_quantity)) AS waiting
                FROM erp_purchase_order o JOIN erp_purchase_order_line l ON l.order_id = o.id
                WHERE o.status IN ('draft', 'ordered')
                GROUP BY l.item_id
                """)
                .query(rs -> {
                    result.put(rs.getInt("item_id"), rs.getBigDecimal("waiting"));
                });
        return result;
    }

    @Override
    public List<StockItem> findItems(Collection<Integer> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        // JPQL: SQL over the mapped classes and fields instead of tables and columns (like LINQ to Entities)
        return em.createQuery("SELECT i FROM StockItem i WHERE i.active = true AND i.id IN :ids", StockItem.class)
                .setParameter("ids", itemIds)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE) // see JpaStockRepository.findItem
                .getResultList();
    }

    @Override
    public void add(PurchaseOrder order) {
        em.persist(order);
    }

    @Override
    public void add(StockMovement movement) {
        em.persist(movement);
    }
}
