package ua.newerp.infrastructure.production;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import ua.newerp.application.catalog.PreOrderStore;
import ua.newerp.application.production.StorefrontStock;
import ua.newerp.application.production.StorefrontSyncResult;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.infrastructure.ErpProperties;

/** Models come through JPA (with the changes of the current transaction); the OpenCart tables change with plain UPDATEs. */
@Repository
@Transactional
class JpaStorefrontStock implements StorefrontStock {

    private final EntityManager em;
    private final JdbcClient jdbc;
    private final PreOrderStore preOrders;
    private final int languageId;

    JpaStorefrontStock(EntityManager em, JdbcClient jdbc, PreOrderStore preOrders, ErpProperties erp) {
        this.em = em;
        this.jdbc = jdbc;
        this.preOrders = preOrders;
        this.languageId = erp.languageId();
    }

    @Override
    public StorefrontSyncResult push(Collection<Integer> productionIds) {
        if (productionIds != null && productionIds.isEmpty()) {
            return new StorefrontSyncResult(0, 0);
        }
        // The links and sizes may have just changed in this transaction; the SQL below must see them
        em.flush();
        List<ProductionModel> models = em.createQuery("SELECT m FROM ProductionModel m WHERE m.active = true", ProductionModel.class)
                .getResultList().stream()
                .filter(m -> productionIds == null || productionIds.contains(m.getId()))
                .toList();
        Map<Integer, Integer> owners = productOwners();

        int modelCount = 0;
        int productCount = 0;
        for (ProductionModel model : models) {
            Map<Integer, Integer> quantities = model.storefrontQuantities();
            List<Integer> productIds = owners.entrySet().stream().filter(o -> o.getValue().equals(model.getId())).map(Map.Entry::getKey).toList();
            if (quantities.isEmpty() || productIds.isEmpty()) {
                continue;
            }

            // Negative stock is what was sold ahead. It is first taken off the pre-order quota, otherwise the new stock
            // would overwrite it and the quota would stay full
            preOrders.settle(productIds);

            // OpenCart tables are MyISAM, without transactions, so plain UPDATEs, as in the old module
            quantities.forEach((optionValueId, quantity) -> jdbc.sql("""
                    UPDATE dd_product_option_value SET quantity = :quantity
                    WHERE product_id IN (:productIds) AND option_value_id = :optionValueId
                    """)
                    .param("quantity", quantity).param("productIds", productIds).param("optionValueId", optionValueId)
                    .update());

            // A product's quantity is the sum of its linked sizes (only of the products that have such sizes)
            Map<Integer, Integer> totals = new HashMap<>();
            jdbc.sql("""
                    SELECT product_id, SUM(quantity) AS total FROM dd_product_option_value
                    WHERE product_id IN (:productIds) AND option_value_id IN (:optionValueIds)
                    GROUP BY product_id
                    """)
                    .param("productIds", productIds).param("optionValueIds", quantities.keySet())
                    .query(rs -> {
                        totals.put(rs.getInt("product_id"), rs.getInt("total"));
                    });
            totals.entrySet().stream()
                    .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.toList())))
                    .forEach((total, ids) -> jdbc.sql("UPDATE dd_product SET quantity = :total WHERE product_id IN (:ids)")
                            .param("total", total).param("ids", ids).update());

            modelCount++;
            productCount += totals.size();
        }
        return new StorefrontSyncResult(modelCount, productCount);
    }

    /**
     * Which model each shop product belongs to. As with the write-off by order: a direct link beats a category, and a category
     * with a name filter beats a whole category. Through a category only enabled products count.
     */
    private Map<Integer, Integer> productOwners() {
        record ByCategory(int productId, int productionId, boolean hasFilter) {
        }
        List<ByCategory> byCategory = jdbc.sql("""
                SELECT pc.product_id, c.production_id, c.name_filter <> '' AS has_filter
                FROM dd_stock_production_categories c
                JOIN dd_stock_production m ON m.production_id = c.production_id
                JOIN dd_product_to_category pc ON pc.category_id = c.oc_category_id
                JOIN dd_product p ON p.product_id = pc.product_id
                JOIN dd_product_description pd ON pd.product_id = pc.product_id AND pd.language_id = :languageId
                WHERE m.status = 1 AND p.status = 1
                  AND (c.name_filter = '' OR (c.match_exact = 1 AND pd.name = c.name_filter)
                       OR (c.match_exact = 0 AND LOCATE(c.name_filter, pd.name) > 0))
                ORDER BY has_filter DESC, c.production_id
                """)
                .param("languageId", languageId)
                .query((rs, n) -> new ByCategory(rs.getInt("product_id"), rs.getInt("production_id"), rs.getBoolean("has_filter")))
                .list();

        var owners = new HashMap<Integer, Integer>();
        byCategory.forEach(c -> owners.putIfAbsent(c.productId(), c.productionId()));
        jdbc.sql("""
                SELECT l.product_id, MIN(l.production_id) AS production_id
                FROM dd_stock_production_products l
                JOIN dd_stock_production m ON m.production_id = l.production_id
                WHERE m.status = 1
                GROUP BY l.product_id
                """)
                .query(rs -> {
                    owners.put(rs.getInt("product_id"), rs.getInt("production_id"));
                });
        return owners;
    }
}
