package ua.newerp.domain.partners;

import java.math.BigDecimal;
import java.util.List;

public record FeedOffer(String id, BigDecimal price, BigDecimal priceOld, String categoryId, List<String> pictures, String model,
        String vendorCode, String name, String description, String size, String barcode) {
}
