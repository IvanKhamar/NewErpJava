package ua.newerp.infrastructure.partners;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import ua.newerp.application.partners.PartnerCategoryMapDto;
import ua.newerp.application.partners.PartnerDto;
import ua.newerp.application.partners.PartnerListItemDto;
import ua.newerp.application.partners.PartnerProductMapDto;
import ua.newerp.application.partners.PartnerProductPickDto;
import ua.newerp.application.partners.PartnerRepository;
import ua.newerp.application.partners.ProductSizePriceDto;
import ua.newerp.application.partners.ShopCategoryDto;
import ua.newerp.domain.partners.FeedProduct;
import ua.newerp.domain.partners.FeedProductOption;
import ua.newerp.domain.partners.Partner;
import ua.newerp.infrastructure.ErpProperties;

/** Partners through Hibernate (dd_partner and its mappings); the OpenCart catalog through SQL. */
@Repository
@Transactional(readOnly = true)
class JpaPartnerRepository implements PartnerRepository {

    // The old module takes as sizes the options whose name contains "Розмір".
    private static final String SIZE_VALUES_SQL = """
            SELECT v.product_id, vd.name, v.price
            FROM dd_product_option_value v
            JOIN dd_option_description od ON od.option_id = v.option_id AND od.language_id = :languageId
            JOIN dd_option_value_description vd ON vd.option_value_id = v.option_value_id AND vd.language_id = :languageId
            WHERE LOCATE('Розмір', od.name) > 0
            """;

    private final EntityManager em;
    private final JdbcClient jdbc;
    private final int languageId;

    JpaPartnerRepository(EntityManager em, JdbcClient jdbc, ErpProperties erp) {
        this.em = em;
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public List<PartnerListItemDto> list() {
        return jdbc.sql("""
                SELECT p.partner_id, p.code, p.name, p.vendor_name, p.feed_enabled, p.status,
                       (SELECT COUNT(*) FROM dd_partner_category_map c WHERE c.partner_id = p.partner_id) AS categories,
                       (SELECT COUNT(*) FROM dd_partner_product_map m WHERE m.partner_id = p.partner_id) AS products
                FROM dd_partner p
                ORDER BY p.name
                """)
                .query((rs, n) -> new PartnerListItemDto(rs.getInt("partner_id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("vendor_name"), rs.getBoolean("feed_enabled"), rs.getBoolean("status"), rs.getInt("categories"),
                        rs.getInt("products")))
                .list();
    }

    @Override
    public Optional<PartnerDto> getDto(int id) {
        Partner partner = em.find(Partner.class, id);
        if (partner == null) {
            return Optional.empty();
        }

        Map<Integer, String> categoryNames = getCategoryNames(partner.getCategoryMap().stream().map(r -> r.getCategoryId()).toList());
        record Product(String name, boolean enabled) {
        }
        List<Integer> productIds = partner.getProductMap().stream().map(r -> r.getProductId()).distinct().toList();
        Map<Integer, Product> products = new HashMap<>();
        if (!productIds.isEmpty()) {
            jdbc.sql("""
                    SELECT p.product_id, COALESCE(d.name, '') AS name, p.status
                    FROM dd_product p
                    LEFT JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :languageId
                    WHERE p.product_id IN (:ids)
                    """)
                    .param("languageId", languageId).param("ids", productIds)
                    .query(rs -> {
                        products.put(rs.getInt("product_id"), new Product(rs.getString("name"), rs.getBoolean("status")));
                    });
        }

        return Optional.of(new PartnerDto(partner.getId(), partner.getCode(), partner.getName(), partner.isActive(), partner.getFeedUrl(),
                partner.isFeedEnabled(), partner.getVendorName(), partner.getNotes(), partner.getDefaultQuantity(), partner.getDateModified(),
                partner.getCategoryMap().stream()
                        .map(r -> new PartnerCategoryMapDto(r.getId(), r.getCategoryId(), categoryNames.getOrDefault(r.getCategoryId(), ""),
                                r.getSizeName(), r.getExternalId(), r.getExternalName(), r.getPercentSupply(), r.getPercentSale()))
                        .toList(),
                partner.getProductMap().stream()
                        .map(r -> {
                            Product p = products.get(r.getProductId());
                            return new PartnerProductMapDto(r.getId(), r.getProductId(), p == null ? "" : p.name(), p != null && p.enabled(),
                                    r.getSizeName(), r.getExternalId(), r.getExternalName(), r.getPriceSupply(), r.getPriceSale());
                        })
                        .toList()));
    }

    @Override
    public Optional<Partner> get(int id) {
        return Optional.ofNullable(em.find(Partner.class, id));
    }

    @Override
    public Optional<Partner> getByCode(String code) {
        return em.createQuery("SELECT p FROM Partner p WHERE p.code = :code", Partner.class)
                .setParameter("code", code)
                .getResultStream().findFirst();
    }

    @Override
    public boolean codeExists(String code, Integer exceptId) {
        return em.createQuery("SELECT COUNT(p) FROM Partner p WHERE p.code = :code AND (:exceptId IS NULL OR p.id <> :exceptId)", Long.class)
                .setParameter("code", code).setParameter("exceptId", exceptId)
                .getSingleResult() > 0;
    }

    @Override
    @Transactional
    public void add(Partner partner) {
        em.persist(partner);
    }

    @Override
    @Transactional
    public void remove(Partner partner) {
        em.remove(partner);
    }

    @Override
    public List<ShopCategoryDto> listCategories() {
        Map<Integer, TreeSet<String>> sizes = new HashMap<>();
        jdbc.sql("SELECT DISTINCT ptc.category_id, s.name FROM dd_product_to_category ptc JOIN (" + SIZE_VALUES_SQL
                + ") s ON s.product_id = ptc.product_id")
                .param("languageId", languageId)
                .query(rs -> {
                    sizes.computeIfAbsent(rs.getInt("category_id"), k -> new TreeSet<>()).add(rs.getString("name"));
                });

        return jdbc.sql("SELECT category_id, name FROM dd_category_description WHERE language_id = :languageId ORDER BY name")
                .param("languageId", languageId)
                .query((rs, n) -> {
                    int id = rs.getInt("category_id");
                    return new ShopCategoryDto(id, rs.getString("name"), List.copyOf(sizes.getOrDefault(id, new TreeSet<>())));
                })
                .list();
    }

    @Override
    public List<PartnerProductPickDto> searchProducts(String term, int limit) {
        record Product(int id, String name, boolean enabled, BigDecimal price) {
        }
        int id;
        try {
            id = Integer.parseInt(term);
        } catch (NumberFormatException e) {
            id = 0;
        }
        List<Product> products = jdbc.sql("""
                SELECT p.product_id, d.name, p.status, p.price
                FROM dd_product p
                JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :languageId
                WHERE LOCATE(:term, d.name) > 0 OR p.product_id = :id
                ORDER BY d.name
                LIMIT :limit
                """)
                .param("languageId", languageId).param("term", term).param("id", id).param("limit", limit)
                .query((rs, n) -> new Product(rs.getInt("product_id"), rs.getString("name"), rs.getBoolean("status"), rs.getBigDecimal("price")))
                .list();
        if (products.isEmpty()) {
            return List.of();
        }

        // The first price of each size name, sizes in ordinal order.
        Map<Integer, Map<String, BigDecimal>> sizes = new HashMap<>();
        jdbc.sql("SELECT * FROM (" + SIZE_VALUES_SQL + ") s WHERE s.product_id IN (:ids)")
                .param("languageId", languageId).param("ids", products.stream().map(Product::id).toList())
                .query(rs -> {
                    sizes.computeIfAbsent(rs.getInt("product_id"), k -> new LinkedHashMap<>()).putIfAbsent(rs.getString("name"), rs.getBigDecimal("price"));
                });

        return products.stream()
                .map(p -> {
                    Map<String, BigDecimal> productSizes = sizes.get(p.id());
                    List<ProductSizePriceDto> prices = productSizes == null
                            ? List.of(new ProductSizePriceDto("", p.price()))
                            : productSizes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                    .map(e -> new ProductSizePriceDto(e.getKey(), e.getValue())).toList();
                    return new PartnerProductPickDto(p.id(), p.name(), p.enabled(), prices);
                })
                .toList();
    }

    @Override
    public List<FeedProduct> getFeedProducts(Collection<Integer> categoryIds, Collection<Integer> productIds) {
        if (categoryIds.isEmpty() && productIds.isEmpty()) {
            return List.of();
        }
        // Not the whole dd_product row: so the "zero" OpenCart dates are not read.
        var where = new ArrayList<String>();
        Map<String, Object> params = new HashMap<>();
        params.put("languageId", languageId);
        if (!productIds.isEmpty()) {
            where.add("p.product_id IN (:productIds)");
            params.put("productIds", productIds);
        }
        if (!categoryIds.isEmpty()) {
            where.add("EXISTS (SELECT 1 FROM dd_product_to_category c WHERE c.product_id = p.product_id AND c.category_id IN (:categoryIds))");
            params.put("categoryIds", categoryIds);
        }
        record Product(int id, String model, String sku, BigDecimal price, String image, String name, String description) {
        }
        List<Product> products = jdbc.sql("""
                SELECT p.product_id, p.model, p.sku, p.price, p.image, COALESCE(d.name, '') AS name, COALESCE(d.description, '') AS description
                FROM dd_product p
                LEFT JOIN dd_product_description d ON d.product_id = p.product_id AND d.language_id = :languageId
                WHERE p.status = 1 AND (""" + String.join(" OR ", where) + ")")
                .params(params)
                .query((rs, n) -> new Product(rs.getInt("product_id"), rs.getString("model"), rs.getString("sku"), rs.getBigDecimal("price"),
                        rs.getString("image"), rs.getString("name"), rs.getString("description")))
                .list();
        if (products.isEmpty()) {
            return List.of();
        }

        List<Integer> ids = products.stream().map(Product::id).toList();
        Map<Integer, List<Integer>> categories = new HashMap<>();
        jdbc.sql("SELECT product_id, category_id FROM dd_product_to_category WHERE product_id IN (:ids) ORDER BY category_id")
                .param("ids", ids)
                .query(rs -> {
                    categories.computeIfAbsent(rs.getInt("product_id"), k -> new ArrayList<>()).add(rs.getInt("category_id"));
                });
        Map<Integer, List<String>> images = new HashMap<>();
        jdbc.sql("SELECT product_id, COALESCE(image, '') AS image FROM dd_product_image WHERE product_id IN (:ids) ORDER BY sort_order, product_image_id")
                .param("ids", ids)
                .query(rs -> {
                    images.computeIfAbsent(rs.getInt("product_id"), k -> new ArrayList<>()).add(rs.getString("image"));
                });
        Map<Integer, List<FeedProductOption>> options = new HashMap<>();
        jdbc.sql("""
                SELECT v.product_id, v.option_value_id, COALESCE(d.name, '') AS name, v.price
                FROM dd_product_option_value v
                LEFT JOIN dd_option_value_description d ON d.option_value_id = v.option_value_id AND d.language_id = :languageId
                WHERE v.product_id IN (:ids)
                ORDER BY v.option_value_id, v.product_option_value_id
                """)
                .param("languageId", languageId).param("ids", ids)
                .query(rs -> {
                    options.computeIfAbsent(rs.getInt("product_id"), k -> new ArrayList<>())
                            .add(new FeedProductOption(rs.getInt("option_value_id"), rs.getString("name"), rs.getBigDecimal("price")));
                });

        return products.stream()
                .map(p -> {
                    var pictures = new ArrayList<String>();
                    if (p.image() != null && !p.image().isEmpty()) {
                        pictures.add(p.image());
                    }
                    pictures.addAll(images.getOrDefault(p.id(), List.of()));
                    return new FeedProduct(p.id(), p.model(), p.sku(), p.price(), p.name(), p.description(),
                            categories.getOrDefault(p.id(), List.of()), pictures, options.getOrDefault(p.id(), List.of()));
                })
                .toList();
    }

    @Override
    public Map<Integer, String> getCategoryNames(Collection<Integer> categoryIds) {
        Map<Integer, String> names = new HashMap<>();
        if (categoryIds.isEmpty()) {
            return names;
        }
        jdbc.sql("SELECT category_id, name FROM dd_category_description WHERE language_id = :languageId AND category_id IN (:ids)")
                .param("languageId", languageId).param("ids", categoryIds.stream().distinct().toList())
                .query(rs -> {
                    names.put(rs.getInt("category_id"), rs.getString("name"));
                });
        return names;
    }
}
