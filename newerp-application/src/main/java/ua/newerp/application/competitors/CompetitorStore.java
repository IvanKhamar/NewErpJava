package ua.newerp.application.competitors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** erp_competitor, erp_competitor_link and erp_competitor_price, with our products' names and prices from dd_product. */
public interface CompetitorStore {

    List<CompetitorDto> competitors();

    Optional<CompetitorDto> competitor(int id);

    int addCompetitor(String name, String siteUrl, String priceRegex, boolean active, LocalDateTime now);

    void updateCompetitor(int id, String name, String siteUrl, String priceRegex, boolean active);

    /** Links of one product, or all when productId is null; by product name, then competitor. */
    List<CompetitorLinkDto> links(Integer productId);

    Optional<CompetitorLinkDto> link(int id);

    boolean linkExists(int competitorId, int productId);

    boolean productExists(int productId);

    int addLink(int competitorId, int productId, String url, LocalDateTime now);

    void deleteLink(int id);

    /** Links of the active competitors, for the check. */
    List<CompetitorLinkDto> activeLinks();

    /** Saves the check; a new price row is written only when the price differs from the last one. */
    void saveCheck(int linkId, BigDecimal price, String source, String error, LocalDateTime now);

    List<CompetitorPriceDto> history(int linkId);

    /** Our current price of each product. */
    Map<Integer, BigDecimal> ourPrices(List<Integer> productIds);
}
