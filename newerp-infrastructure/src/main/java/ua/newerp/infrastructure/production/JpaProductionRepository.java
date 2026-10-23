package ua.newerp.infrastructure.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import ua.newerp.application.production.CategoryLinkDto;
import ua.newerp.application.production.OrderSizeLine;
import ua.newerp.application.production.ComponentBomLineDto;
import ua.newerp.application.production.ComponentDto;
import ua.newerp.application.production.ProductionLogDto;
import ua.newerp.application.production.ProductionLogFilter;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionModelFilter;
import ua.newerp.application.production.ProductionModelSummaryDto;
import ua.newerp.application.production.PlanOrder;
import ua.newerp.application.production.ProductionRepository;
import ua.newerp.application.production.SewingPlanSource;
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
import ua.newerp.infrastructure.ErpProperties;

/** Tables of the old StockTrack module dd_stock_production* (structure reconstructed, see db/reconstructed.sql). */
@Repository
@Transactional(readOnly = true)
class JpaProductionRepository implements ProductionRepository, SewingPlanSource {

    private record ItemName(String name, String unit) {
    }

    private record ProductLink(int productId, int productionId, boolean hasFilter) {
    }

    private final EntityManager em;
    private final JdbcClient jdbc;
    private final int languageId;

    JpaProductionRepository(EntityManager em, JdbcClient jdbc, ErpProperties properties) {
        this.em = em;
        this.jdbc = jdbc;
        this.languageId = properties.languageId();
    }

    @Override
    public List<OrderSizeLine> getOrderSizeLines(int orderId) {
        return sizeLines(List.of(orderId)).getOrDefault(orderId, List.of());
    }

    /** Lines of several orders with the production model and options, by order id (the sewing plan needs many orders). */
    private Map<Integer, List<OrderSizeLine>> sizeLines(Collection<Integer> orderIds) {
        record Product(int orderId, int orderProductId, int productId, String name, int quantity) {
        }
        List<Product> products = jdbc.sql("""
                SELECT order_id, order_product_id, product_id, name, quantity FROM dd_order_product
                WHERE order_id IN (:ids) ORDER BY order_id, order_product_id
                """)
                .param("ids", orderIds)
                .query((rs, n) -> new Product(rs.getInt("order_id"), rs.getInt("order_product_id"), rs.getInt("product_id"),
                        rs.getString("name"), rs.getInt("quantity")))
                .list();
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Integer> productIds = products.stream().map(Product::productId).distinct().toList();

        // An option in the order points to product_option_value, and a model size to the option_value_id from it.
        Map<Integer, List<Integer>> options = new HashMap<>();
        jdbc.sql("""
                SELECT o.order_product_id, v.option_value_id
                FROM dd_order_option o
                JOIN dd_product_option_value v ON v.product_option_value_id = o.product_option_value_id
                WHERE o.order_id IN (:ids)
                """)
                .param("ids", orderIds)
                .query(rs -> {
                    options.computeIfAbsent(rs.getInt("order_product_id"), k -> new ArrayList<>()).add(rs.getInt("option_value_id"));
                });

        Map<Integer, Integer> direct = new HashMap<>();
        jdbc.sql("""
                SELECT l.product_id, MIN(l.production_id) AS production_id
                FROM dd_stock_production_products l
                JOIN dd_stock_production m ON m.production_id = l.production_id
                WHERE m.status = 1 AND l.product_id IN (:ids)
                GROUP BY l.product_id
                """)
                .param("ids", productIds)
                .query(rs -> {
                    direct.put(rs.getInt("product_id"), rs.getInt("production_id"));
                });

        // Link through a category: an empty filter means the whole category, otherwise the product name contains the
        // filter (or equals it, if match_exact). As in the old module, case-insensitive (the database collation).
        List<ProductLink> byCategory = jdbc.sql("""
                SELECT pc.product_id, c.production_id, c.name_filter <> '' AS has_filter
                FROM dd_stock_production_categories c
                JOIN dd_stock_production m ON m.production_id = c.production_id
                JOIN dd_product_to_category pc ON pc.category_id = c.oc_category_id
                JOIN dd_product_description pd ON pd.product_id = pc.product_id
                WHERE m.status = 1 AND pd.language_id = :lang AND pc.product_id IN (:ids)
                  AND (c.name_filter = ''
                       OR (c.match_exact = 1 AND pd.name = c.name_filter)
                       OR (c.match_exact = 0 AND LOCATE(c.name_filter, pd.name) > 0))
                """)
                .param("lang", languageId)
                .param("ids", productIds)
                .query((rs, n) -> new ProductLink(rs.getInt("product_id"), rs.getInt("production_id"), rs.getBoolean("has_filter")))
                .list();

        // A direct link wins over a category, and a category with a filter wins over a whole category.
        Function<Integer, Integer> productionFor = productId -> direct.containsKey(productId)
                ? direct.get(productId)
                : byCategory.stream()
                        .filter(c -> c.productId() == productId)
                        .sorted(Comparator.comparing(ProductLink::hasFilter).reversed().thenComparingInt(ProductLink::productionId))
                        .map(ProductLink::productionId)
                        .findFirst()
                        .orElse(null);

        Map<Integer, List<OrderSizeLine>> result = new LinkedHashMap<>();
        products.forEach(p -> result.computeIfAbsent(p.orderId(), k -> new ArrayList<>()).add(new OrderSizeLine(p.productId(), p.name(),
                p.quantity(), productionFor.apply(p.productId()), options.getOrDefault(p.orderProductId(), List.of()))));
        return result;
    }

    @Override
    public List<PlanOrder> getPlanOrders(Collection<Integer> statusIds) {
        if (statusIds.isEmpty()) {
            return List.of();
        }
        record Order(int id, LocalDateTime dateAdded) {
        }
        List<Order> orders = jdbc.sql("SELECT order_id, date_added FROM dd_order WHERE order_status_id IN (:statuses) ORDER BY order_id")
                .param("statuses", statusIds)
                .query((rs, n) -> new Order(rs.getInt("order_id"), rs.getTimestamp("date_added").toLocalDateTime()))
                .list();
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = orders.stream().map(Order::id).toList();
        Map<Integer, List<OrderSizeLine>> lines = sizeLines(ids);
        Map<Integer, Map<Integer, BigDecimal>> held = sewnHeld(ids);
        return orders.stream()
                .map(o -> new PlanOrder(o.id(), o.dateAdded(), lines.getOrDefault(o.id(), List.of()), held.getOrDefault(o.id(), Map.of())))
                .toList();
    }

    /** The same as getSewnHeldByOrder, but for many orders in one query. */
    private Map<Integer, Map<Integer, BigDecimal>> sewnHeld(List<Integer> orderIds) {
        record Key(int orderId, boolean taken) {
        }
        Map<String, Key> byComment = new HashMap<>();
        orderIds.forEach(id -> {
            byComment.put(ProductionModel.orderComment(id), new Key(id, true));
            byComment.put(ProductionModel.cancelComment(id), new Key(id, false));
        });
        Map<Integer, Map<Integer, BigDecimal>> held = new HashMap<>();
        jdbc.sql("SELECT size_id, direction, comment, quantity FROM dd_stock_production_log WHERE stage = 'sewn' AND comment IN (:comments)")
                .param("comments", byComment.keySet())
                .query(rs -> {
                    Key key = byComment.get(rs.getString("comment"));
                    String direction = rs.getString("direction");
                    if (key == null || !direction.equals(key.taken() ? "out" : "in")) {
                        return;
                    }
                    BigDecimal quantity = rs.getBigDecimal("quantity");
                    held.computeIfAbsent(key.orderId(), k -> new HashMap<>())
                            .merge(rs.getInt("size_id"), key.taken() ? quantity : quantity.negate(), BigDecimal::add);
                });
        return held;
    }

    @Override
    public Map<Integer, Integer> getProductionDays(Collection<Integer> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> days = new HashMap<>();
        jdbc.sql("""
                SELECT pc.product_id, c.production_period FROM dd_product_to_category pc
                JOIN dd_category c ON c.category_id = pc.category_id
                WHERE pc.product_id IN (:ids)
                ORDER BY pc.category_id
                """)
                .param("ids", productIds)
                .query(rs -> {
                    days.putIfAbsent(rs.getInt("product_id"), rs.getInt("production_period"));
                });
        return days;
    }

    @Override
    public List<ProductionModel> getPlanModels(Collection<Integer> productionIds) {
        if (productionIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT m FROM ProductionModel m WHERE m.id IN :ids", ProductionModel.class)
                .setParameter("ids", productionIds)
                .getResultList();
    }

    @Override
    public List<ProductionModel> getActiveClothingModels() {
        return em.createQuery("SELECT m FROM ProductionModel m WHERE m.active = true AND m.type = :type", ProductionModel.class)
                .setParameter("type", ProductionModel.CLOTHING)
                .getResultList();
    }

    @Override
    public List<ProductionComponent> getComponents(Collection<Integer> productionIds) {
        if (productionIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("""
                SELECT c FROM ProductionComponent c WHERE c.productionId IN :ids ORDER BY c.sortOrder, c.name
                """, ProductionComponent.class)
                .setParameter("ids", productionIds)
                .getResultList();
    }

    @Override
    public List<ProductionModel> findModels(Collection<Integer> productionIds) {
        if (productionIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT m FROM ProductionModel m WHERE m.id IN :ids", ProductionModel.class)
                .setParameter("ids", productionIds)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    @Override
    public Map<Integer, BigDecimal> getSewnHeldByOrder(int orderId) {
        Map<Integer, BigDecimal> held = new HashMap<>();
        jdbc.sql("""
                SELECT size_id,
                       SUM(CASE WHEN comment = :taken AND direction = 'out' THEN quantity ELSE 0 END)
                     - SUM(CASE WHEN comment = :returned AND direction = 'in' THEN quantity ELSE 0 END) AS held
                FROM dd_stock_production_log
                WHERE stage = 'sewn' AND comment IN (:taken, :returned)
                GROUP BY size_id
                """)
                .param("taken", ProductionModel.orderComment(orderId))
                .param("returned", ProductionModel.cancelComment(orderId))
                .query(rs -> {
                    held.put(rs.getInt("size_id"), rs.getBigDecimal("held"));
                });
        return held;
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
