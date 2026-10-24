package ua.newerp.infrastructure.competitors;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.competitors.CompetitorDto;
import ua.newerp.application.competitors.CompetitorLinkDto;
import ua.newerp.application.competitors.CompetitorPriceDto;
import ua.newerp.application.competitors.CompetitorStore;
import ua.newerp.infrastructure.ErpProperties;

/** The erp_competitor* tables; product names come from dd_product_description, our prices from dd_product. */
@Repository
class JdbcCompetitorStore implements CompetitorStore {

    private static final String COMPETITOR = """
            SELECT c.id, c.name, c.site_url, c.price_regex, c.active,
                   (SELECT COUNT(*) FROM erp_competitor_link l WHERE l.competitor_id = c.id) AS links
            FROM erp_competitor c
            """;

    private static final String LINK = """
            SELECT l.id, l.competitor_id, c.name AS competitor_name, l.product_id, COALESCE(pd.name, '') AS product_name, l.url,
                   l.last_price, l.last_checked_at, l.last_error
            FROM erp_competitor_link l
            JOIN erp_competitor c ON c.id = l.competitor_id
            LEFT JOIN dd_product_description pd ON pd.product_id = l.product_id AND pd.language_id = :languageId
            """;

    private static final String LINK_ORDER = " ORDER BY product_name, c.name, l.id";

    private final JdbcClient jdbc;
    private final int languageId;

    JdbcCompetitorStore(JdbcClient jdbc, ErpProperties erp) {
        this.jdbc = jdbc;
        this.languageId = erp.languageId();
    }

    @Override
    public List<CompetitorDto> competitors() {
        return jdbc.sql(COMPETITOR + " ORDER BY c.name, c.id").query((rs, n) -> competitor(rs)).list();
    }

    @Override
    public Optional<CompetitorDto> competitor(int id) {
        return jdbc.sql(COMPETITOR + " WHERE c.id = :id").param("id", id).query((rs, n) -> competitor(rs)).optional();
    }

    @Override
    public int addCompetitor(String name, String siteUrl, String priceRegex, boolean active, LocalDateTime now) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO erp_competitor (name, site_url, price_regex, active, created_at)
                VALUES (:name, :site, :regex, :active, :now)
                """)
                .param("name", name).param("site", siteUrl).param("regex", priceRegex).param("active", active).param("now", now)
                .update(keys);
        return keys.getKey().intValue();
    }

    @Override
    public void updateCompetitor(int id, String name, String siteUrl, String priceRegex, boolean active) {
        jdbc.sql("UPDATE erp_competitor SET name = :name, site_url = :site, price_regex = :regex, active = :active WHERE id = :id")
                .param("name", name).param("site", siteUrl).param("regex", priceRegex).param("active", active).param("id", id)
                .update();
    }

    @Override
    public List<CompetitorLinkDto> links(Integer productId) {
        if (productId == null) {
            return jdbc.sql(LINK + LINK_ORDER).param("languageId", languageId).query((rs, n) -> link(rs)).list();
        }
        return jdbc.sql(LINK + " WHERE l.product_id = :productId" + LINK_ORDER)
                .param("languageId", languageId).param("productId", productId).query((rs, n) -> link(rs)).list();
    }

    @Override
    public Optional<CompetitorLinkDto> link(int id) {
        return jdbc.sql(LINK + " WHERE l.id = :id").param("languageId", languageId).param("id", id).query((rs, n) -> link(rs)).optional();
    }

    @Override
    public boolean linkExists(int competitorId, int productId) {
        return jdbc.sql("SELECT COUNT(*) FROM erp_competitor_link WHERE competitor_id = :c AND product_id = :p")
                .param("c", competitorId).param("p", productId).query(Integer.class).single() > 0;
    }

    @Override
    public boolean productExists(int productId) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_product WHERE product_id = :id").param("id", productId).query(Integer.class).single() > 0;
    }

    @Override
    public int addLink(int competitorId, int productId, String url, LocalDateTime now) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO erp_competitor_link (competitor_id, product_id, url, created_at)
                VALUES (:c, :p, :url, :now)
                """)
                .param("c", competitorId).param("p", productId).param("url", url).param("now", now)
                .update(keys);
        return keys.getKey().intValue();
    }

    @Override
    public void deleteLink(int id) {
        jdbc.sql("DELETE FROM erp_competitor_price WHERE link_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM erp_competitor_link WHERE id = :id").param("id", id).update();
    }

    @Override
    public List<CompetitorLinkDto> activeLinks() {
        return jdbc.sql(LINK + " WHERE c.active = 1" + LINK_ORDER).param("languageId", languageId).query((rs, n) -> link(rs)).list();
    }

    @Override
    public void saveCheck(int linkId, BigDecimal price, String source, String error, LocalDateTime now) {
        if (price == null) {
            jdbc.sql("UPDATE erp_competitor_link SET last_checked_at = :now, last_error = :error WHERE id = :id")
                    .param("now", now).param("error", error).param("id", linkId).update();
            return;
        }
        Optional<BigDecimal> last = jdbc.sql("SELECT price FROM erp_competitor_price WHERE link_id = :id ORDER BY checked_at DESC, id DESC LIMIT 1")
                .param("id", linkId).query(BigDecimal.class).optional();
        if (last.isEmpty() || last.get().compareTo(price) != 0) {
            jdbc.sql("INSERT INTO erp_competitor_price (link_id, price, source, checked_at) VALUES (:id, :price, :source, :now)")
                    .param("id", linkId).param("price", price).param("source", source).param("now", now).update();
        }
        jdbc.sql("UPDATE erp_competitor_link SET last_price = :price, last_checked_at = :now, last_error = '' WHERE id = :id")
                .param("price", price).param("now", now).param("id", linkId).update();
    }

    @Override
    public List<CompetitorPriceDto> history(int linkId) {
        return jdbc.sql("SELECT price, source, checked_at FROM erp_competitor_price WHERE link_id = :id ORDER BY checked_at DESC, id DESC")
                .param("id", linkId)
                .query((rs, n) -> new CompetitorPriceDto(rs.getBigDecimal("price"), rs.getString("source"), time(rs, "checked_at")))
                .list();
    }

    @Override
    public Map<Integer, BigDecimal> ourPrices(List<Integer> productIds) {
        var prices = new HashMap<Integer, BigDecimal>();
        if (productIds.isEmpty()) {
            return prices;
        }
        jdbc.sql("SELECT product_id, price FROM dd_product WHERE product_id IN (:ids)").param("ids", productIds)
                .query(rs -> {
                    prices.put(rs.getInt("product_id"), rs.getBigDecimal("price"));
                });
        return prices;
    }

    private static CompetitorDto competitor(ResultSet rs) throws SQLException {
        return new CompetitorDto(rs.getInt("id"), rs.getString("name"), rs.getString("site_url"), rs.getString("price_regex"),
                rs.getBoolean("active"), rs.getInt("links"));
    }

    private static CompetitorLinkDto link(ResultSet rs) throws SQLException {
        return new CompetitorLinkDto(rs.getInt("id"), rs.getInt("competitor_id"), rs.getString("competitor_name"),
                rs.getInt("product_id"), rs.getString("product_name"), rs.getString("url"), rs.getBigDecimal("last_price"),
                time(rs, "last_checked_at"), rs.getString("last_error"));
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
