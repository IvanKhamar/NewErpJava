package ua.newerp.infrastructure.catalog;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ua.newerp.application.catalog.PreOrderFilter;
import ua.newerp.application.catalog.PreOrderRow;
import ua.newerp.application.catalog.PreOrderStore;
import ua.newerp.domain.catalog.PreOrder;
import ua.newerp.infrastructure.ErpProperties;

/** Pre-order quotas live in OpenCart's dd_product_option_value (pre_order_quantity, date_available). */
@Repository
class JdbcPreOrderStore implements PreOrderStore {

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcPreOrderStore(JdbcClient jdbc, ErpProperties erp) {
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public List<PreOrderRow> list(PreOrderFilter filter, int limit) {
        // date_available as text: the old site can write '0000-00-00', which a date cannot hold
        return jdbc.sql("""
                SELECT v.product_option_value_id, v.product_id, d.name, p.model, COALESCE(vd.name, '') AS size, v.quantity,
                       v.pre_order_quantity, v.subtract, CAST(v.date_available AS CHAR) AS date_available
                FROM dd_product_option_value v
                JOIN dd_product p ON p.product_id = v.product_id
                JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :languageId
                LEFT JOIN dd_option_value_description vd ON vd.option_value_id = v.option_value_id AND vd.language_id = :languageId
                WHERE (:search IS NULL OR LOCATE(:search, d.name) > 0 OR LOCATE(:search, p.model) > 0)
                  AND (:onlyWithQuota = 0 OR v.pre_order_quantity > 0)
                ORDER BY d.name, v.product_id, v.product_option_value_id
                LIMIT :limit
                """)
                .param("languageId", languageId).param("search", filter.search()).param("onlyWithQuota", filter.withQuotaOnly() ? 1 : 0)
                .param("limit", limit)
                .query((rs, n) -> {
                    int quantity = rs.getInt("quantity");
                    int quota = rs.getInt("pre_order_quantity");
                    return new PreOrderRow(rs.getInt("product_option_value_id"), rs.getInt("product_id"), rs.getString("name"),
                            rs.getString("model"), rs.getString("size"), quantity, quota, date(rs.getString("date_available")),
                            PreOrder.availability(quantity, quota, rs.getBoolean("subtract")).ordinal());
                })
                .list();
    }

    @Override
    public boolean update(int productOptionValueId, int quota, LocalDate dateAvailable) {
        return jdbc.sql("UPDATE dd_product_option_value SET pre_order_quantity = :quota, date_available = :date WHERE product_option_value_id = :id")
                .param("quota", quota).param("date", dateAvailable).param("id", productOptionValueId)
                .update() > 0;
    }

    @Override
    public int settle(Collection<Integer> productIds) {
        if (productIds != null && productIds.isEmpty()) {
            return 0;
        }
        record Row(int id, int quantity, int quota) {
        }
        Map<String, Object> params = new HashMap<>();
        String where = "quantity < 0 AND pre_order_quantity > 0";
        if (productIds != null) {
            where += " AND product_id IN (:productIds)";
            params.put("productIds", productIds);
        }
        List<Row> rows = jdbc.sql("SELECT product_option_value_id, quantity, pre_order_quantity FROM dd_product_option_value WHERE " + where)
                .params(params)
                .query((rs, n) -> new Row(rs.getInt("product_option_value_id"), rs.getInt("quantity"), rs.getInt("pre_order_quantity")))
                .list();

        // MyISAM without transactions, so each size is a separate UPDATE with a condition on the old values: if the storefront
        // sold more in the meantime, the row does not change and is settled next time
        int changed = 0;
        for (Row row : rows) {
            PreOrder.Settled settled = PreOrder.settle(row.quantity(), row.quota());
            changed += jdbc.sql("""
                    UPDATE dd_product_option_value SET quantity = :quantity, pre_order_quantity = :quota
                    WHERE product_option_value_id = :id AND quantity = :oldQuantity AND pre_order_quantity = :oldQuota
                    """)
                    .param("quantity", settled.quantity()).param("quota", settled.quota()).param("id", row.id())
                    .param("oldQuantity", row.quantity()).param("oldQuota", row.quota())
                    .update();
        }
        return changed;
    }

    private static LocalDate date(String text) {
        if (text == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(text);
            return date.getYear() > 1 ? date : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
