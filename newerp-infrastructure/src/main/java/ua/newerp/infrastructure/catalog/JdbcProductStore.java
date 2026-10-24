package ua.newerp.infrastructure.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.catalog.ProductCardDto;
import ua.newerp.application.catalog.ProductFilter;
import ua.newerp.application.catalog.ProductListItem;
import ua.newerp.application.catalog.ProductStore;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.PagedResult;
import ua.newerp.application.common.Paging;
import ua.newerp.infrastructure.ErpProperties;

/** Shop products straight from OpenCart's tables, in plain SQL like the order list. */
@Repository
@Transactional(readOnly = true)
class JdbcProductStore implements ProductStore {

    private static final String COLUMNS = """
            SELECT p.product_id, COALESCE(d.name, '') AS name, p.model, p.price, p.status, p.quantity, p.image, p.date_modified
            FROM dd_product p
            LEFT JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :lang
            """;

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcProductStore(JdbcClient jdbc, ErpProperties properties) {
        this.jdbc = jdbc;
        this.languageId = properties.languageId();
    }

    @Override
    public PagedResult<ProductListItem> list(ProductFilter filter) {
        Paging paging = Paging.normalize(filter.page(), filter.pageSize());
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        params.put("lang", languageId);

        if (filter.categoryId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM dd_product_to_category fc WHERE fc.product_id = p.product_id AND fc.category_id = :categoryId)");
            params.put("categoryId", filter.categoryId());
        }
        if (filter.labelId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM dd_product_to_label fl WHERE fl.product_id = p.product_id AND fl.label_id = :labelId)");
            params.put("labelId", filter.labelId());
        }
        if (filter.enabled() != null) {
            where.append(" AND p.status = :status");
            params.put("status", filter.enabled() ? 1 : 0);
        }
        String search = filter.search() == null ? "" : filter.search().strip();
        if (!search.isEmpty()) {
            params.put("like", "%" + escapeLike(search) + "%");
            if (isInt(search)) {
                where.append(" AND (p.product_id = :productId OR p.model LIKE :like)");
                params.put("productId", Integer.parseInt(search));
            } else {
                where.append(" AND (d.name LIKE :like OR p.model LIKE :like)");
            }
        }

        int total = jdbc.sql("""
                SELECT COUNT(*) FROM dd_product p
                LEFT JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :lang""" + where)
                .params(params).query(Integer.class).single();
        params.put("limit", paging.pageSize());
        params.put("offset", paging.offset());
        List<ProductListItem> items = jdbc.sql(COLUMNS + where + " ORDER BY name, p.product_id LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> new ProductListItem(rs.getInt("product_id"), rs.getString("name"), rs.getString("model"),
                        rs.getBigDecimal("price"), rs.getInt("status") == 1, rs.getInt("quantity"), rs.getString("image")))
                .list();
        return PagedResult.of(items, paging.page(), paging.pageSize(), total);
    }

    @Override
    public Optional<ProductCardDto> find(int productId) {
        return jdbc.sql(COLUMNS + " WHERE p.product_id = :id")
                .param("lang", languageId).param("id", productId)
                .query((rs, n) -> new ProductCardDto(rs.getInt("product_id"), rs.getString("name"), rs.getString("model"),
                        rs.getBigDecimal("price"), rs.getInt("status") == 1, rs.getInt("quantity"), rs.getString("image"),
                        rs.getObject("date_modified", LocalDateTime.class), List.of(), List.of(), List.of()))
                .optional()
                .map(this::withDetails);
    }

    @Override
    public List<LookupItem> labels() {
        return jdbc.sql("SELECT label_id, name FROM dd_product_label ORDER BY sort_order, name")
                .query((rs, n) -> new LookupItem(rs.getInt("label_id"), rs.getString("name")))
                .list();
    }

    @Override
    public List<LookupItem> categories() {
        return jdbc.sql("""
                SELECT c.category_id, d.name FROM dd_category c
                JOIN dd_category_description d ON d.category_id = c.category_id AND d.language_id = :lang
                WHERE c.status = 1 ORDER BY d.name
                """)
                .param("lang", languageId)
                .query((rs, n) -> new LookupItem(rs.getInt("category_id"), rs.getString("name")))
                .list();
    }

    // dd_product is MyISAM: no transaction, no row lock. date_modified is the version: the UPDATE only hits the row the user saw,
    // and OpenCart's own save also sets date_modified, so a change made there in between is noticed too.
    @Override
    @Transactional
    public boolean update(int productId, LocalDateTime expected, BigDecimal price, Boolean enabled) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", productId);
        params.put("expected", expected);
        params.put("price", price);
        params.put("status", enabled == null ? null : enabled ? 1 : 0);
        return jdbc.sql("""
                UPDATE dd_product SET price = COALESCE(:price, price), status = COALESCE(:status, status), date_modified = NOW()
                WHERE product_id = :id AND date_modified = :expected
                """)
                .params(params).update() == 1;
    }

    @Override
    @Transactional
    public void replaceLabels(int productId, Collection<Integer> labelIds) {
        jdbc.sql("DELETE FROM dd_product_to_label WHERE product_id = :id").param("id", productId).update();
        for (int labelId : labelIds) {
            jdbc.sql("INSERT INTO dd_product_to_label (product_id, label_id) VALUES (:id, :labelId)")
                    .param("id", productId).param("labelId", labelId).update();
        }
    }

    @Override
    @Transactional
    public void replaceCategories(int productId, Collection<Integer> categoryIds) {
        jdbc.sql("DELETE FROM dd_product_to_category WHERE product_id = :id").param("id", productId).update();
        for (int categoryId : categoryIds) {
            jdbc.sql("INSERT INTO dd_product_to_category (product_id, category_id) VALUES (:id, :categoryId)")
                    .param("id", productId).param("categoryId", categoryId).update();
        }
    }

    /** The card's sizes, labels and categories, read after the product row. */
    private ProductCardDto withDetails(ProductCardDto p) {
        int productId = p.id();
        // A size without a name in this language still shows, as its number.
        List<ProductCardDto.Size> sizes = jdbc.sql("""
                SELECT v.option_value_id, COALESCE(n.name, CONCAT('#', v.option_value_id)) AS name, v.quantity
                FROM dd_product_option_value v
                LEFT JOIN dd_option_value_description n ON n.option_value_id = v.option_value_id AND n.language_id = :lang
                WHERE v.product_id = :id
                ORDER BY v.product_option_value_id
                """)
                .param("lang", languageId).param("id", productId)
                .query((r, n) -> new ProductCardDto.Size(r.getInt("option_value_id"), r.getString("name"), r.getInt("quantity")))
                .list();
        List<LookupItem> labels = jdbc.sql("""
                SELECT l.label_id, l.name FROM dd_product_to_label pl JOIN dd_product_label l ON l.label_id = pl.label_id
                WHERE pl.product_id = :id ORDER BY l.sort_order, l.name
                """)
                .param("id", productId)
                .query((r, n) -> new LookupItem(r.getInt("label_id"), r.getString("name")))
                .list();
        List<LookupItem> categories = jdbc.sql("""
                SELECT pc.category_id, COALESCE(d.name, CONCAT('#', pc.category_id)) AS name FROM dd_product_to_category pc
                LEFT JOIN dd_category_description d ON d.category_id = pc.category_id AND d.language_id = :lang
                WHERE pc.product_id = :id ORDER BY name
                """)
                .param("lang", languageId).param("id", productId)
                .query((r, n) -> new LookupItem(r.getInt("category_id"), r.getString("name")))
                .list();
        return new ProductCardDto(p.id(), p.name(), p.model(), p.price(), p.enabled(), p.quantity(), p.image(), p.dateModified(), sizes, labels,
                categories);
    }

    /** LIKE treats % and _ as wildcards; escape them so a search for "50%" finds exactly that. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean isInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
