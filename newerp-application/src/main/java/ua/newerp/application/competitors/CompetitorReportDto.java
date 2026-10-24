package ua.newerp.application.competitors;

import java.math.BigDecimal;
import java.util.List;

/**
 * Our price against the competitors' last prices, one row per watched product. competitors are the columns; each row's prices
 * follow them (null: the competitor has no page or no price for it). difference = how much our price is above (+) or below (−) the
 * lowest competitor price, in percent.
 */
public record CompetitorReportDto(List<Column> competitors, List<Row> rows) {

    public record Column(int id, String name) {
    }

    public record Row(int productId, String productName, BigDecimal ourPrice, List<BigDecimal> prices, BigDecimal lowest,
            BigDecimal differencePercent) {
    }
}
