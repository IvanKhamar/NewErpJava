package ua.newerp.infrastructure.production;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import ua.newerp.application.production.CategoryLinkDto;
import ua.newerp.application.production.ComponentBomLineDto;
import ua.newerp.application.production.ComponentDto;
import ua.newerp.application.production.ProductionLogDto;
import ua.newerp.application.production.ProductionLogFilter;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionModelFilter;
import ua.newerp.application.production.ProductionModelSummaryDto;
import ua.newerp.application.production.ProductionRepository;
import ua.newerp.application.production.ProductionSizeDto;
import ua.newerp.application.production.SewnBomLineDto;
import ua.newerp.domain.production.ComponentBomLine;
import ua.newerp.domain.production.ComponentStock;
import ua.newerp.domain.production.ProductionComponent;
import ua.newerp.domain.production.ProductionLogEntry;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.domain.production.ProductionProductLink;
import ua.newerp.domain.production.ProductionSize;
import ua.newerp.domain.production.SewnBomLine;
import ua.newerp.domain.stock.StockItem;
import ua.newerp.domain.stock.StockMovement;

/** Tables of the old StockTrack module dd_stock_production* (structure reconstructed, see db/reconstructed.sql). */
@Repository
@Transactional(readOnly = true)
class JpaProductionRepository implements ProductionRepository {

    private record ItemName(String name, String unit) {
    }

    private final EntityManager em;
    private final JdbcClient jdbc;

    JpaProductionRepository(EntityManager em, JdbcClient jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public List<ProductionModelSummaryDto> list(ProductionModelFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.production_id, m.name, m.type, m.min_total,
                       COALESCE(SUM(s.cut_stock), 0) AS cut, COALESCE(SUM(s.sewn_stock), 0) AS sewn
                FROM dd_stock_production m
                LEFT JOIN dd_stock_production_sizes s ON s.production_id = m.production_id
                WHERE m.status = 1
                """);
        Map<String, Object> params = new HashMap<>();
        if (filter.type() != null && !filter.type().isEmpty()) {
            sql.append(" AND m.type = :type");
            params.put("type", filter.type());
        }
        sql.append(" GROUP BY m.production_id, m.name, m.type, m.min_total, m.sort_order ORDER BY m.sort_order, m.name");

        return jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> {
                    int minTotal = rs.getInt("min_total");
                    BigDecimal sewn = rs.getBigDecimal("sewn");
                    return new ProductionModelSummaryDto(rs.getInt("production_id"), rs.getString("name"), rs.getString("type"),
                            minTotal, rs.getBigDecimal("cut"), sewn, BigDecimal.valueOf(minTotal).subtract(sewn).max(BigDecimal.ZERO));
                })
                .list()
                .stream()
                .filter(r -> !filter.onlyBelowMinimum() || r.deficit().signum() > 0)
                .toList();
    }

    @Override
    public Optional<ProductionModelDto> getDto(int productionId) {
        ProductionModel model = em.find(ProductionModel.class, productionId);
        if (model == null || !model.isActive()) {
            return Optional.empty();
        }

        List<ProductionComponent> components = findComponents(productionId);
        List<ComponentStock> stock = findComponentStocks(components.stream().map(ProductionComponent::getId).toList());
        List<Integer> itemIds = Stream.concat(
                components.stream().flatMap(c -> c.getBom().stream()).map(ComponentBomLine::getItemId),
                model.getSewnBom().stream().map(SewnBomLine::getItemId).filter(Objects::nonNull))
                .distinct().toList();
        Map<Integer, ItemName> items = new HashMap<>();
        if (!itemIds.isEmpty()) {
            jdbc.sql("SELECT item_id, name, unit FROM dd_stock_items WHERE item_id IN (:ids)").param("ids", itemIds)
                    .query(rs -> {
                        items.put(rs.getInt("item_id"), new ItemName(rs.getString("name"), rs.getString("unit")));
                    });
        }
        Map<Integer, String> componentNames = components.stream()
                .collect(Collectors.toMap(ProductionComponent::getId, ProductionComponent::getName));
        ItemName none = new ItemName(null, null);

        return Optional.of(new ProductionModelDto(
                model.getId(), model.getName(), model.getType(), model.getMinTotal(), model.getSortOrder(),
                model.getSizes().stream()
                        .sorted(Comparator.comparingInt(ProductionSize::getSortOrder))
                        .map(s -> new ProductionSizeDto(s.getId(), s.getName(), s.getPercent(), s.getCutStock(), s.getSewnStock(),
                                s.getOptionValueId()))
                        .toList(),
                model.getProductLinks().stream().map(ProductionProductLink::getProductId).sorted().toList(),
                model.getCategoryLinks().stream()
                        .map(l -> new CategoryLinkDto(l.getCategoryId(), l.getNameFilter(), l.isMatchExact()))
                        .toList(),
                components.stream()
                        .map(c -> new ComponentDto(c.getId(), c.getName(), c.getSortOrder(),
                                stock.stream()
                                        .filter(s -> s.getComponentId() == c.getId())
                                        .collect(Collectors.toMap(ComponentStock::getSizeId, ComponentStock::getStock)),
                                c.getBom().stream()
                                        .map(b -> {
                                            ItemName item = items.getOrDefault(b.getItemId(), none);
                                            return new ComponentBomLineDto(b.getSizeId(), b.getItemId(), item.name(), item.unit(),
                                                    b.getQtyPerUnit());
                                        })
                                        .toList()))
                        .toList(),
                model.getSewnBom().stream()
                        .map(b -> {
                            ItemName item = b.getItemId() == null ? none : items.getOrDefault(b.getItemId(), none);
                            return new SewnBomLineDto(b.getSizeId(), b.getComponentId(),
                                    b.getComponentId() == null ? null : componentNames.get(b.getComponentId()),
                                    b.getItemId(), item.name(), item.unit(), b.getQtyPerUnit());
                        })
                        .toList()));
    }

    @Override
    public Optional<ProductionModel> findModel(int productionId) {
        // SELECT ... FOR UPDATE on the model row: operations on one model (here or a second request) wait for each other.
        return Optional.ofNullable(em.find(ProductionModel.class, productionId, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public Optional<ProductionComponent> findComponent(int componentId) {
        return Optional.ofNullable(em.find(ProductionComponent.class, componentId));
    }

    @Override
    public List<Integer> componentIds(int productionId) {
        return em.createQuery("SELECT c.id FROM ProductionComponent c WHERE c.productionId = :id", Integer.class)
                .setParameter("id", productionId)
                .getResultList();
    }

    @Override
    public List<Integer> missingStockItemIds(Collection<Integer> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        List<Integer> existing = jdbc.sql("SELECT item_id FROM dd_stock_items WHERE status = 1 AND item_id IN (:ids)")
                .param("ids", itemIds)
                .query(Integer.class)
                .list();
        return itemIds.stream().filter(id -> !existing.contains(id)).toList();
    }

    @Override
    public boolean sizesInUse(Collection<Integer> sizeIds) {
        return jdbc.sql("""
                SELECT (SELECT COUNT(*) FROM dd_stock_production_component_bom WHERE size_id IN (:ids))
                     + (SELECT COUNT(*) FROM dd_stock_production_component_stock WHERE size_id IN (:ids) AND stock <> 0)
                """)
                .param("ids", sizeIds)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    public List<ComponentStock> findComponentStock(int componentId) {
        return em.createQuery("SELECT s FROM ComponentStock s WHERE s.componentId = :id", ComponentStock.class)
                .setParameter("id", componentId)
                .getResultList();
    }

    @Override
    public List<ProductionComponent> findComponents(int productionId) {
        return em.createQuery("""
                SELECT c FROM ProductionComponent c WHERE c.productionId = :id ORDER BY c.sortOrder, c.name
                """, ProductionComponent.class)
                .setParameter("id", productionId)
                .getResultList();
    }

    @Override
    public List<ComponentStock> findComponentStocks(Collection<Integer> componentIds) {
        if (componentIds.isEmpty()) {
            return new ArrayList<>();
        }
        return em.createQuery("SELECT s FROM ComponentStock s WHERE s.componentId IN :ids", ComponentStock.class)
                .setParameter("ids", componentIds)
                .getResultList();
    }

    @Override
    public List<StockItem> findStockItems(Collection<Integer> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT i FROM StockItem i WHERE i.active = true AND i.id IN :ids", StockItem.class)
                .setParameter("ids", itemIds)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    @Override
    public List<ProductionLogDto> listLog(ProductionLogFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.log_id, l.production_id, m.name AS production_name, l.size_id, l.size_name, l.stage, l.direction,
                       l.quantity, l.stock_before, l.stock_after, l.comment, l.user_id, l.username, l.date_added
                FROM dd_stock_production_log l
                LEFT JOIN dd_stock_production m ON m.production_id = l.production_id
                WHERE 1 = 1
                """);
        Map<String, Object> params = new HashMap<>();
        if (filter.productionId() != null) {
            sql.append(" AND l.production_id = :productionId");
            params.put("productionId", filter.productionId());
        }
        if (filter.stage() != null && !filter.stage().isEmpty()) {
            sql.append(" AND l.stage = :stage");
            params.put("stage", filter.stage());
        }
        sql.append(" ORDER BY l.date_added DESC, l.log_id DESC LIMIT :limit");
        params.put("limit", filter.limit());

        return jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new ProductionLogDto(
                        rs.getInt("log_id"), rs.getInt("production_id"), rs.getString("production_name"), rs.getInt("size_id"),
                        rs.getString("size_name"), rs.getString("stage"), rs.getString("direction"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("stock_before"), rs.getBigDecimal("stock_after"), rs.getString("comment"),
                        rs.getInt("user_id"), rs.getString("username"), rs.getTimestamp("date_added").toLocalDateTime()))
                .list();
    }

    @Override
    public void add(ProductionLogEntry entry) {
        em.persist(entry);
    }

    @Override
    public void add(StockMovement movement) {
        em.persist(movement);
    }

    @Override
    public void add(ComponentStock stock) {
        em.persist(stock);
    }

    @Override
    public void add(ProductionModel model) {
        em.persist(model);
    }

    @Override
    public void add(ProductionComponent component) {
        em.persist(component);
    }

    @Override
    public void remove(ProductionComponent component) {
        em.remove(component);
    }

    @Override
    public void remove(ComponentStock stock) {
        em.remove(stock);
    }
}
