package ua.newerp.domain.partners;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ua.newerp.domain.common.Html;

/**
 * Builds a partner's feed the same way as catalog/model/feed/eva.php of the old site: offers from categories (the size's price
 * plus the category's markup) and single products (fixed prices), each size a separate offer.
 * Offer ids and barcodes are the same as the old ones, so the partner does not lose the links to its products.
 */
public final class PartnerFeedBuilder {

    private static final Pattern LINE_BREAK_TAGS = Pattern.compile("</p>|<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    private PartnerFeedBuilder() {
    }

    /**
     * @param products      enabled products from the mapped categories and from the list of single products
     * @param categoryNames shop category names (for categories without the partner's own name)
     * @param storeUrl      the shop address ending with "/"; photo paths are added to it
     */
    public static PartnerFeed build(Partner partner, Collection<FeedProduct> products, Map<Integer, String> categoryNames, String storeUrl) {
        // Key "category|size"; an empty size is the whole category. As in the old code, a later row overrides an earlier one
        var mapIndex = new HashMap<String, PartnerCategoryMap>();
        List<PartnerCategoryMap> categoryRows = partner.getCategoryMap().stream()
                .sorted(Comparator.comparing(PartnerCategoryMap::getId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        categoryRows.forEach(row -> mapIndex.put(key(row.getCategoryId(), row.getSizeName()), row));
        Set<Integer> mappedCategoryIds = categoryRows.stream().map(PartnerCategoryMap::getCategoryId).collect(Collectors.toSet());
        Map<Integer, FeedProduct> productsById = products.stream().collect(Collectors.toMap(FeedProduct::productId, Function.identity()));

        var standalone = new LinkedHashMap<String, FeedOffer>();
        var extraCategories = new TreeMap<Integer, String>();
        for (PartnerProductMap row : partner.getProductMap().stream()
                .sorted(Comparator.comparing(PartnerProductMap::getId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList()) {
            FeedProduct product = productsById.get(row.getProductId());
            if (product == null) {
                continue;
            }

            int categoryId = product.categoryIds().stream().min(Integer::compare).orElse(0);
            PartnerCategoryMap categoryRow = resolve(mapIndex, categoryId, row.getSizeName());
            String feedCategoryId;
            if (categoryRow != null) {
                feedCategoryId = feedId(categoryRow);
            } else {
                feedCategoryId = "p" + categoryId;
                extraCategories.put(categoryId, categoryNames.getOrDefault(categoryId, ""));
            }

            if (!product.options().isEmpty()) {
                for (int i = 0; i < product.options().size(); i++) {
                    FeedProductOption option = product.options().get(i);
                    if (row.getSizeName() != null && !option.name().strip().equals(row.getSizeName())) {
                        continue;
                    }
                    String id = "p" + product.productId() + "_" + option.optionValueId();
                    standalone.put(id, offer(id, product, option, row.getPriceSupply() != null ? row.getPriceSupply() : option.price(),
                            row.getPriceSale(), feedCategoryId,
                            !row.getExternalName().isEmpty() ? row.getExternalName() : (product.name() + " - " + option.name()).strip(),
                            row.getExternalId(), i, storeUrl));
                }
            } else {
                String id = "p" + product.productId();
                standalone.put(id, offer(id, product, null, row.getPriceSupply() != null ? row.getPriceSupply() : product.price(),
                        row.getPriceSale(), feedCategoryId, !row.getExternalName().isEmpty() ? row.getExternalName() : product.name(),
                        row.getExternalId(), 0, storeUrl));
            }
        }

        // A product added as a single row is not repeated by an offer from a category (the old feed gave both)
        var offers = new ArrayList<FeedOffer>();
        for (FeedProduct product : products.stream().sorted(Comparator.comparingInt(FeedProduct::productId)).toList()) {
            List<Integer> categoryIds = product.categoryIds().stream().filter(mappedCategoryIds::contains).toList();
            if (categoryIds.isEmpty()) {
                continue;
            }
            int categoryId = categoryIds.stream().min(Integer::compare).orElseThrow();

            if (!product.options().isEmpty()) {
                for (int i = 0; i < product.options().size(); i++) {
                    FeedProductOption option = product.options().get(i);
                    PartnerCategoryMap categoryRow = resolve(mapIndex, categoryId, option.name());
                    if (categoryRow == null || standalone.containsKey("p" + product.productId() + "_" + option.optionValueId())) {
                        continue;
                    }
                    offers.add(categoryOffer(product.productId() + "_" + option.optionValueId(), product, option, option.price(), categoryRow,
                            (product.name() + " - " + option.name()).strip(), i, storeUrl));
                }
            } else {
                PartnerCategoryMap categoryRow = resolve(mapIndex, categoryId, null);
                if (categoryRow == null || standalone.containsKey("p" + product.productId())) {
                    continue;
                }
                offers.add(categoryOffer(Integer.toString(product.productId()), product, null, product.price(), categoryRow, product.name(), 0,
                        storeUrl));
            }
        }
        offers.addAll(standalone.values());

        List<FeedCategory> categories = Stream.concat(
                categoryRows.stream().map(r -> new FeedCategory(feedId(r), r.getExternalId().isEmpty() ? null : r.getExternalId(),
                        !r.getExternalName().isEmpty() ? r.getExternalName() : categoryNames.getOrDefault(r.getCategoryId(), ""))),
                extraCategories.entrySet().stream().map(c -> new FeedCategory("p" + c.getKey(), null, c.getValue())))
                .toList();

        // The partner does not accept offers without photos, so they are skipped, as in the old feed
        return new PartnerFeed(categories, offers.stream().filter(o -> !o.pictures().isEmpty()).toList());
    }

    /**
     * An EAN-13 barcode with Ukraine's prefix 482: 4 digits of the SKU (if it is numeric), 4 digits of the product id,
     * a variant digit (the size's position) and the check digit.
     */
    public static String barcode(String sku, int productId, int variant) {
        String skuPart = !sku.isEmpty() && sku.chars().allMatch(c -> c >= '0' && c <= '9') ? padLeft(left(sku, 4)) : "0000";
        String idPart = padLeft(left(Integer.toString(productId), 4));
        String payload = "482" + skuPart + idPart + (variant % 10);

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (payload.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        int check = (10 - sum % 10) % 10;
        return payload + check;
    }

    /** The description for the partner: the HTML from the database turned into plain text with line breaks. */
    public static String plainDescription(String html) {
        // OpenCart stores the description with escaped entities (&lt;p&gt;), so it is decoded twice, as in the old feed
        String text = Html.decode(Html.decode(html == null ? "" : html));
        text = LINE_BREAK_TAGS.matcher(text).replaceAll("\n");
        return TAGS.matcher(text).replaceAll("").strip();
    }

    private static FeedOffer categoryOffer(String id, FeedProduct product, FeedProductOption option, BigDecimal basePrice,
            PartnerCategoryMap categoryRow, String name, int variant, String storeUrl) {
        BigDecimal supply = categoryRow.getPercentSupply() == null ? BigDecimal.ZERO : categoryRow.getPercentSupply();
        BigDecimal price = basePrice.multiply(BigDecimal.ONE.add(supply.movePointLeft(2)));
        BigDecimal priceOld = categoryRow.getPercentSale() == null
                ? null
                : basePrice.multiply(BigDecimal.ONE.add(categoryRow.getPercentSale().movePointLeft(2)));
        return offer(id, product, option, price, priceOld, feedId(categoryRow), name, null, variant, storeUrl);
    }

    private static FeedOffer offer(String id, FeedProduct product, FeedProductOption option, BigDecimal price, BigDecimal priceOld,
            String categoryId, String name, String vendorCode, int variant, String storeUrl) {
        return new FeedOffer(id,
                round(price),
                priceOld == null ? null : round(priceOld),
                categoryId,
                product.images().stream().filter(i -> !i.isEmpty()).map(i -> pictureUrl(storeUrl, i)).toList(),
                product.model(),
                vendorCode == null || vendorCode.isEmpty() ? null : vendorCode,
                name,
                plainDescription(product.description()),
                option == null || option.name().isEmpty() ? null : option.name(),
                barcode(product.sku(), product.productId(), variant));
    }

    private static PartnerCategoryMap resolve(Map<String, PartnerCategoryMap> mapIndex, int categoryId, String sizeName) {
        if (sizeName != null && mapIndex.containsKey(key(categoryId, sizeName))) {
            return mapIndex.get(key(categoryId, sizeName));
        }
        return mapIndex.get(key(categoryId, null));
    }

    /** The row's id is the category id in the feed (0 for a row not saved yet, as in .NET). */
    private static String feedId(PartnerCategoryMap row) {
        return row.getId() == null ? "0" : row.getId().toString();
    }

    private static String key(int categoryId, String sizeName) {
        return categoryId + "|" + (sizeName == null ? "" : sizeName.strip());
    }

    /** Each path segment is percent-encoded like Uri.EscapeDataString (a space is %20, not +). */
    private static String pictureUrl(String storeUrl, String path) {
        return storeUrl + "image/" + Arrays.stream(path.split("/", -1))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~"))
                .collect(Collectors.joining("/"));
    }

    /** Like PHP's round(): a half goes away from zero. */
    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String left(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private static String padLeft(String value) {
        return "0".repeat(4 - value.length()) + value;
    }
}
