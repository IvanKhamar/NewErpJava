package ua.newerp.infrastructure.catalog;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.catalog.CrossSellItemDto;
import ua.newerp.application.catalog.CrossSellStore;
import ua.newerp.application.common.LookupItem;
import ua.newerp.domain.catalog.CrossSellItem;
import ua.newerp.infrastructure.ErpProperties;

/** dd_crosssell_item with the names of its products and categories. */
@Repository
class JdbcCrossSellStore implements CrossSellStore {

    // A category line counts its enabled products; a product line is 1 while the product is enabled
    private static final String SELECT = """
            SELECT i.crosssell_item_id, i.type, i.target_id, i.discount,
                   CASE WHEN i.type = 'product' THEN COALESCE(pd.name, '') ELSE COALESCE(cd.name, '') END AS name,
                   CASE WHEN i.type = 'product' THEN COALESCE(p.status, 0)
                        ELSE (SELECT COUNT(*) FROM dd_product_to_category pc JOIN dd_product cp ON cp.product_id = pc.product_id
                              WHERE pc.category_id = i.target_id AND cp.status = 1) END AS products
            FROM dd_crosssell_item i
            LEFT JOIN dd_product p ON i.type = 'product' AND p.product_id = i.target_id
            LEFT JOIN dd_product_description pd ON i.type = 'product' AND pd.product_id = i.target_id AND pd.language_id = :languageId
            LEFT JOIN dd_category_description cd ON i.type = 'category' AND cd.category_id = i.target_id AND cd.language_id = :languageId
            """;

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcCrossSellStore(JdbcClient jdbc, ErpProperties erp) {
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public List<CrossSellItemDto> list() {
        return jdbc.sql(SELECT + " ORDER BY i.type = 'product', name").param("languageId", languageId).query((rs, n) -> dto(rs)).list();
    }

    @Override
    public Optional<CrossSellItemDto> find(int id) {
        return jdbc.sql(SELECT + " WHERE i.crosssell_item_id = :id").param("languageId", languageId).param("id", id)
                .query((rs, n) -> dto(rs)).optional();
    }

    @Override
    public Optional<Integer> findId(String type, int targetId) {
        return jdbc.sql("SELECT crosssell_item_id FROM dd_crosssell_item WHERE type = :type AND target_id = :target LIMIT 1")
                .param("type", type).param("target", targetId).query(Integer.class).optional();
    }

    @Override
    public boolean targetExists(String type, int targetId) {
        String sql = CrossSellItem.PRODUCT.equals(type) ? "SELECT COUNT(*) FROM dd_product WHERE product_id = :id"
                : "SELECT COUNT(*) FROM dd_category WHERE category_id = :id";
        return jdbc.sql(sql).param("id", targetId).query(Integer.class).single() > 0;
    }

    @Override
    public int add(CrossSellItem item) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO dd_crosssell_item (type, target_id, discount) VALUES (:type, :target, :discount)")
                .param("type", item.type()).param("target", item.targetId()).param("discount", item.discount())
                .update(keys);
        return keys.getKey().intValue();
    }

    @Override
    public void setDiscount(int id, BigDecimal discount) {
        jdbc.sql("UPDATE dd_crosssell_item SET discount = :discount WHERE crosssell_item_id = :id")
                .param("discount", discount).param("id", id).update();
    }

    @Override
    public void delete(int id) {
        jdbc.sql("DELETE FROM dd_crosssell_item WHERE crosssell_item_id = :id").param("id", id).update();
    }

    @Override
    public List<LookupItem> categories() {
        return jdbc.sql("""
                SELECT c.category_id, d.name FROM dd_category c
                JOIN dd_category_description d ON d.category_id = c.category_id AND d.language_id = :languageId
                WHERE c.status = 1 ORDER BY d.name
                """)
                .param("languageId", languageId)
                .query((rs, n) -> new LookupItem(rs.getInt("category_id"), rs.getString("name")))
                .list();
    }

    private static CrossSellItemDto dto(ResultSet rs) throws SQLException {
        return new CrossSellItemDto(rs.getInt("crosssell_item_id"), rs.getString("type"), rs.getInt("target_id"), rs.getString("name"),
                rs.getBigDecimal("discount"), rs.getInt("products"));
    }
}
