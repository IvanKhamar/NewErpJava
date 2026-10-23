package ua.newerp.infrastructure.catalog;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.catalog.ProductSortProperties;
import ua.newerp.application.catalog.ProductSortStore;
import ua.newerp.application.catalog.SortPinDto;
import ua.newerp.application.catalog.SortProductDto;
import ua.newerp.application.catalog.SortSnapshotDto;
import ua.newerp.domain.catalog.SortGroup;
import ua.newerp.domain.catalog.SortGroupType;
import ua.newerp.domain.catalog.SortPin;
import ua.newerp.infrastructure.ErpProperties;

/** The product order lives in OpenCart tables (dd_product.sort_order, filters, labels) and the old module's own tables. */
@Repository
class JdbcProductSortStore implements ProductSortStore {

    private static final String CROSS_SELL_METRIC_KEY = "crosssell_sort_metric";
    private static final int NO_CROSS_SELL_POSITION = 999999;
    private static final int UPDATE_CHUNK = 500;

    private final JdbcClient jdbc;
    private final int languageId;
    private final ProductSortProperties options;
    private final Clock clock;

    JdbcProductSortStore(JdbcClient jdbc, ErpProperties erp, ProductSortProperties options, Clock clock) {
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
        this.options = options;
        this.clock = clock;
    }

    @Override
    public List<SortGroup> listGroups() {
        var groups = new ArrayList<SortGroup>();

        jdbc.sql("""
                SELECT f.filter_id, fd.name FROM dd_filter f
                JOIN dd_filter_description fd ON fd.filter_id = f.filter_id AND fd.language_id = :languageId
                WHERE f.filter_group_id = :groupId
                ORDER BY f.filter_id
                """)
                .param("languageId", languageId).param("groupId", options.tagFilterGroupId())
                .query(rs -> {
                    String name = rs.getString("name");
                    groups.add(new SortGroup(SortGroupType.TAG, name, "За темою: " + tagTitle(name), null, rs.getInt("filter_id"), null));
                });

        // Only categories with at least one enabled product. A category with products in colors splits into groups by color.
        List<Integer> categoryIds = jdbc.sql("""
                SELECT DISTINCT pc.category_id FROM dd_product_to_category pc
                JOIN dd_product p ON p.product_id = pc.product_id
                WHERE p.status = 1
                ORDER BY pc.category_id
                """)
                .query(Integer.class).list();
        record Color(int filterId, String name) {
        }
        Map<Integer, List<Color>> colors = new HashMap<>();
        if (!options.colorFilterIds().isEmpty()) {
            jdbc.sql("""
                    SELECT DISTINCT pc.category_id, pf.filter_id, fd.name FROM dd_product_to_category pc
                    JOIN dd_product p ON p.product_id = pc.product_id
                    JOIN dd_product_filter pf ON pf.product_id = pc.product_id
                    JOIN dd_filter_description fd ON fd.filter_id = pf.filter_id AND fd.language_id = :languageId
                    WHERE p.status = 1 AND pf.filter_id IN (:colorIds)
                    ORDER BY pf.filter_id
                    """)
                    .param("languageId", languageId).param("colorIds", options.colorFilterIds())
                    .query(rs -> {
                        colors.computeIfAbsent(rs.getInt("category_id"), k -> new ArrayList<>()).add(new Color(rs.getInt("filter_id"), rs.getString("name")));
                    });
        }
        Map<Integer, String> categoryNames = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            jdbc.sql("SELECT category_id, name FROM dd_category_description WHERE language_id = :languageId AND category_id IN (:ids)")
                    .param("languageId", languageId).param("ids", categoryIds)
                    .query(rs -> {
                        categoryNames.put(rs.getInt("category_id"), rs.getString("name"));
                    });
        }
        for (int categoryId : categoryIds) {
            String name = categoryNames.getOrDefault(categoryId, "Категорія #" + categoryId);
            List<Color> categoryColors = colors.get(categoryId);
            if (categoryColors != null) {
                for (Color c : categoryColors) {
                    groups.add(new SortGroup(SortGroupType.CATEGORY_COLOR, categoryId + "_" + c.filterId(), name + " — " + c.name(), categoryId,
                            c.filterId(), null));
                }
            } else {
                groups.add(new SortGroup(SortGroupType.CATEGORY, Integer.toString(categoryId), name, categoryId, null, null));
            }
        }

        jdbc.sql("SELECT label_id, name FROM dd_product_label WHERE status = 1 ORDER BY label_id")
                .query(rs -> {
                    String name = rs.getString("name");
                    groups.add(new SortGroup(SortGroupType.LABEL, name, "Мітка: " + name, null, null, rs.getInt("label_id")));
                });

        groups.add(new SortGroup(SortGroupType.CROSS_SELL, SortGroup.CROSS_SELL_KEY, "Допродаж (оформлення)", null, null, null));
        return groups;
    }

    @Override
    public List<Integer> groupProductIds(SortGroup group) {
        return switch (group.type()) {
            case CATEGORY, CATEGORY_COLOR -> jdbc.sql("""
                    SELECT p.product_id FROM dd_product p
                    WHERE p.status = 1
                      AND EXISTS (SELECT 1 FROM dd_product_to_category c WHERE c.product_id = p.product_id AND c.category_id = :categoryId)
                      AND (:filterId IS NULL
                           OR EXISTS (SELECT 1 FROM dd_product_filter f WHERE f.product_id = p.product_id AND f.filter_id = :filterId))
                    ORDER BY p.product_id
                    """)
                    .param("categoryId", group.categoryId()).param("filterId", group.filterId())
                    .query(Integer.class).list();
            case TAG -> jdbc.sql("""
                    SELECT p.product_id FROM dd_product p
                    WHERE p.status = 1
                      AND EXISTS (SELECT 1 FROM dd_product_filter f WHERE f.product_id = p.product_id AND f.filter_id = :filterId)
                    ORDER BY p.product_id
                    """)
                    .param("filterId", group.filterId())
                    .query(Integer.class).list();
            case LABEL -> jdbc.sql("""
                    SELECT p.product_id FROM dd_product p
                    WHERE p.status = 1
                      AND EXISTS (SELECT 1 FROM dd_product_to_label l WHERE l.product_id = p.product_id AND l.label_id = :labelId)
                    ORDER BY p.date_added DESC, p.product_id
                    """)
                    .param("labelId", group.labelId())
                    .query(Integer.class).list();
            case CROSS_SELL -> crossSellProducts();
        };
    }

    @Override
    public Map<Integer, Integer> sales(Collection<Integer> productIds, LocalDateTime from, LocalDateTime to, boolean crossSellOnly) {
        if (productIds.isEmpty() || options.countedStatusIds().isEmpty()) {
            return Map.of();
        }
        var sql = new StringBuilder("""
                SELECT op.product_id, SUM(op.quantity) AS sales
                FROM dd_order_product op
                JOIN dd_order o ON o.order_id = op.order_id
                WHERE op.product_id IN (:productIds) AND o.date_added >= :from AND o.date_added <= :to
                  AND o.order_status_id IN (:statuses)
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("productIds", productIds);
        params.put("from", from);
        params.put("to", to);
        params.put("statuses", options.countedStatusIds());
        if (!options.excludedUserIds().isEmpty()) {
            sql.append(" AND o.user_id NOT IN (:excludedUsers)");
            params.put("excludedUsers", options.excludedUserIds());
        }
        if (crossSellOnly) {
            sql.append(" AND op.crosssell = 1");
        }
        // Wholesale and partner orders do not show what people buy in the shop (LOCATE in MySQL ignores case).
        int n = 0;
        for (String marker : options.excludedOrderMarkers()) {
            String p = "orderMarker" + n++;
            sql.append(" AND LOCATE(:").append(p).append(", o.firstname) = 0 AND LOCATE(:").append(p).append(", o.lastname) = 0")
                    .append(" AND LOCATE(:").append(p).append(", o.payment_company) = 0 AND LOCATE(:").append(p).append(", o.comment) = 0");
            params.put(p, marker);
        }
        for (String marker : options.excludedCommentMarkers()) {
            String p = "commentMarker" + n++;
            sql.append(" AND LOCATE(:").append(p).append(", o.comment) = 0");
            params.put(p, marker);
        }
        sql.append(" GROUP BY op.product_id");

        Map<Integer, Integer> sales = new HashMap<>();
        jdbc.sql(sql.toString()).params(params)
                .query(rs -> {
                    sales.put(rs.getInt("product_id"), rs.getInt("sales"));
                });
        return sales;
    }

    @Override
    public List<SortProductDto> products(Collection<Integer> productIds, boolean crossSell) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT p.product_id, COALESCE(d.name, '') AS name, COALESCE(p.image, '') AS image,
                       CASE WHEN :crossSell = 1 THEN COALESCE(cs.sort_order, :none) ELSE p.sort_order END AS sort_order
                FROM dd_product p
                LEFT JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :languageId
                LEFT JOIN dd_crosssell_sort cs ON cs.product_id = p.product_id
                WHERE p.product_id IN (:ids)
                """)
                .param("crossSell", crossSell ? 1 : 0).param("none", NO_CROSS_SELL_POSITION).param("languageId", languageId)
                .param("ids", productIds)
                .query((rs, n) -> new SortProductDto(rs.getInt("product_id"), rs.getString("name"), rs.getString("image"), rs.getInt("sort_order")))
                .list();
    }

    @Override
    public int apply(boolean crossSell, List<Integer> orderedProductIds) {
        if (crossSell) {
            // The panel's contents may have changed, so its order is rewritten whole, as on the old site.
            jdbc.sql("DELETE FROM dd_crosssell_sort").update();
            for (int i = 0; i < orderedProductIds.size(); i++) {
                jdbc.sql("INSERT INTO dd_crosssell_sort (product_id, sort_order) VALUES (:id, :position)")
                        .param("id", orderedProductIds.get(i)).param("position", i + 1).update();
            }
            return orderedProductIds.size();
        }

        // One UPDATE … CASE for a few hundred products instead of a query for each. Only numbers go into the SQL.
        for (int start = 0; start < orderedProductIds.size(); start += UPDATE_CHUNK) {
            var sql = new StringBuilder("UPDATE dd_product SET sort_order = CASE product_id");
            var ids = new StringBuilder();
            for (int i = start; i < Math.min(start + UPDATE_CHUNK, orderedProductIds.size()); i++) {
                int id = orderedProductIds.get(i);
                sql.append(" WHEN ").append(id).append(" THEN ").append(i + 1);
                ids.append(ids.isEmpty() ? "" : ",").append(id);
            }
            sql.append(" END WHERE product_id IN (").append(ids).append(')');
            jdbc.sql(sql.toString()).update();
        }
        return orderedProductIds.size();
    }

    @Override
    public List<SortPin> pins(SortGroup group) {
        return jdbc.sql("SELECT position, product_id FROM dd_product_manual_pin WHERE group_type = :type AND group_key = :key ORDER BY position, id")
                .param("type", group.typeCode()).param("key", group.key())
                .query((rs, n) -> new SortPin(rs.getInt("position"), rs.getInt("product_id")))
                .list();
    }

    @Override
    public List<SortPinDto> listPins() {
        var pins = new ArrayList<SortPinDto>();
        jdbc.sql("""
                SELECT pin.id, pin.group_type, pin.group_key, pin.position, pin.product_id, COALESCE(d.name, '') AS name
                FROM dd_product_manual_pin pin
                LEFT JOIN dd_product_description d ON d.product_id = pin.product_id AND d.language_id = :languageId
                ORDER BY pin.group_type, pin.group_key, pin.position
                """)
                .param("languageId", languageId)
                .query(rs -> {
                    SortGroupType type = knownType(rs.getString("group_type"));
                    if (type != null) {
                        pins.add(new SortPinDto(rs.getInt("id"), type.ordinal(), rs.getString("group_key"), rs.getInt("position"),
                                rs.getInt("product_id"), rs.getString("name")));
                    }
                });
        return pins;
    }

    @Override
    public void setPin(SortGroup group, int position, int productId, LocalDateTime now) {
        jdbc.sql("""
                DELETE FROM dd_product_manual_pin
                WHERE group_type = :type AND group_key = :key AND (product_id = :productId OR position = :position)
                """)
                .param("type", group.typeCode()).param("key", group.key()).param("productId", productId).param("position", position)
                .update();
        jdbc.sql("""
                INSERT INTO dd_product_manual_pin (group_type, group_key, position, product_id, created_at)
                VALUES (:type, :key, :position, :productId, :now)
                """)
                .param("type", group.typeCode()).param("key", group.key()).param("position", position).param("productId", productId)
                .param("now", now)
                .update();
    }

    @Override
    public boolean removePin(int id) {
        return jdbc.sql("DELETE FROM dd_product_manual_pin WHERE id = :id").param("id", id).update() > 0;
    }

    @Override
    public int createSnapshot(String note, LocalDateTime now) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO dd_product_order_snapshot (note, created_at) VALUES (:note, :now)")
                .param("note", note.length() > 255 ? note.substring(0, 255) : note).param("now", now)
                .update(keys);
        int id = keys.getKey().intValue();
        jdbc.sql("INSERT INTO dd_product_order_snapshot_item (snapshot_id, product_id, sort_order) SELECT :id, product_id, sort_order FROM dd_product")
                .param("id", id)
                .update();
        return id;
    }

    @Override
    public List<SortSnapshotDto> listSnapshots(int limit) {
        return jdbc.sql("SELECT snapshot_id, note, created_at FROM dd_product_order_snapshot ORDER BY created_at DESC, snapshot_id DESC LIMIT :limit")
                .param("limit", limit)
                .query((rs, n) -> new SortSnapshotDto(rs.getInt("snapshot_id"), rs.getString("note"),
                        rs.getObject("created_at", LocalDateTime.class)))
                .list();
    }

    @Override
    public boolean restoreSnapshot(int id) {
        boolean exists = jdbc.sql("SELECT COUNT(*) FROM dd_product_order_snapshot WHERE snapshot_id = :id").param("id", id)
                .query(Integer.class).single() > 0;
        if (!exists) {
            return false;
        }
        jdbc.sql("""
                UPDATE dd_product p JOIN dd_product_order_snapshot_item s ON s.product_id = p.product_id
                SET p.sort_order = s.sort_order WHERE s.snapshot_id = :id
                """)
                .param("id", id)
                .update();
        return true;
    }

    @Override
    public boolean crossSellPanelOnly() {
        return jdbc.sql("SELECT COUNT(*) FROM dd_setting WHERE store_id = 0 AND `key` = :key AND value = 'panel'")
                .param("key", CROSS_SELL_METRIC_KEY)
                .query(Integer.class).single() > 0;
    }

    @Override
    public void setCrossSellPanelOnly(boolean panelOnly) {
        jdbc.sql("DELETE FROM dd_setting WHERE store_id = 0 AND `key` = :key").param("key", CROSS_SELL_METRIC_KEY).update();
        jdbc.sql("INSERT INTO dd_setting (store_id, code, `key`, value, serialized) VALUES (0, 'crosssell_sort', :key, :value, 0)")
                .param("key", CROSS_SELL_METRIC_KEY).param("value", panelOnly ? "panel" : "all")
                .update();
    }

    @Override
    public List<SortProductDto> searchProducts(String term, int limit) {
        return jdbc.sql("""
                SELECT p.product_id, d.name, COALESCE(p.image, '') AS image, p.sort_order
                FROM dd_product p
                JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :languageId
                WHERE p.status = 1 AND LOCATE(:term, d.name) > 0
                ORDER BY d.name
                LIMIT :limit
                """)
                .param("languageId", languageId).param("term", term).param("limit", limit)
                .query((rs, n) -> new SortProductDto(rs.getInt("product_id"), rs.getString("name"), rs.getString("image"), rs.getInt("sort_order")))
                .list();
    }

    /**
     * The cross-sell panel's contents, as on the storefront: single products and products of the chosen categories, enabled,
     * in store 0 and already available, by the product's current order.
     */
    private List<Integer> crossSellProducts() {
        return jdbc.sql("""
                SELECT p.product_id FROM dd_product p
                WHERE p.status = 1 AND p.date_available <= :now
                  AND EXISTS (SELECT 1 FROM dd_product_to_store s WHERE s.product_id = p.product_id AND s.store_id = 0)
                  AND (EXISTS (SELECT 1 FROM dd_crosssell_item i WHERE i.type = 'product' AND i.target_id = p.product_id)
                       OR EXISTS (SELECT 1 FROM dd_product_to_category pc
                                  JOIN dd_crosssell_item i ON i.type = 'category' AND i.target_id = pc.category_id
                                  WHERE pc.product_id = p.product_id))
                ORDER BY p.sort_order, p.product_id
                """)
                .param("now", LocalDateTime.now(clock))
                .query(Integer.class).list();
    }

    private static SortGroupType knownType(String code) {
        for (SortGroupType type : SortGroupType.values()) {
            if (type.code().equals(code)) {
                return type;
            }
        }
        return null;
    }

    // Like ucfirst(str_replace(':', ' ', name)) in the old code.
    private static String tagTitle(String name) {
        String title = name.replace(':', ' ');
        return title.isEmpty() ? title : title.substring(0, 1).toUpperCase(Locale.ROOT) + title.substring(1);
    }
}
