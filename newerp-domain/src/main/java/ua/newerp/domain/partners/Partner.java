package ua.newerp.domain.partners;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;

import ua.newerp.domain.common.DomainException;

/**
 * A reseller partner (dd_partner): gets an XML feed of our products with its own categories and prices.
 * Products get into the feed through the category mapping (with a markup in percent) or as single rows (with fixed prices).
 */
public class Partner {

    public static final int CODE_MAX_LENGTH = 32;
    public static final int NAME_MAX_LENGTH = 128;

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final BigDecimal MINUS_HUNDRED = BigDecimal.valueOf(-100);

    private Integer id;
    private String code = "";
    private String name = "";
    private boolean active = true;
    private String feedUrl = "";
    private boolean feedEnabled;
    private String vendorName = "";
    private String notes = "";
    private int defaultQuantity;
    private LocalDateTime dateAdded;
    private LocalDateTime dateModified;
    private List<PartnerCategoryMap> categoryMap = new ArrayList<>();
    private List<PartnerProductMap> productMap = new ArrayList<>();

    protected Partner() {
    }

    public Partner(PartnerSettings settings, LocalDateTime now) {
        update(settings, now);
        dateAdded = now;
    }

    public void update(PartnerSettings settings, LocalDateTime now) {
        String newCode = settings.code() == null ? "" : settings.code().strip();
        if (newCode.isEmpty() || newCode.length() > CODE_MAX_LENGTH || !CODE.matcher(newCode).matches()) {
            throw new DomainException("Код партнера — від 1 до " + CODE_MAX_LENGTH + " латинських літер, цифр, «-» чи «_».");
        }
        String newName = settings.name() == null ? "" : settings.name().strip();
        if (newName.isEmpty() || newName.length() > NAME_MAX_LENGTH) {
            throw new DomainException("Назва партнера — від 1 до " + NAME_MAX_LENGTH + " символів.");
        }
        if (settings.defaultQuantity() < 0) {
            throw new DomainException("Кількість у фіді не може бути від'ємною.");
        }

        code = newCode;
        name = newName;
        active = settings.isActive();
        feedUrl = limit(settings.feedUrl(), 255, "Адреса фіда");
        feedEnabled = settings.feedEnabled();
        vendorName = limit(settings.vendorName(), 64, "Бренд");
        notes = settings.notes() == null ? "" : settings.notes().strip();
        defaultQuantity = settings.defaultQuantity();
        dateModified = now;
    }

    /**
     * Replaces the category mapping. Rows with the same category and size are updated in place, so their ids (the category ids
     * in the feed) do not change. The old site deleted and created all rows again.
     */
    public void setCategoryMap(List<CategoryMapping> rows) {
        List<CategoryMapping> mappings = rows.stream().map(r -> new CategoryMapping(r.categoryId(), normalizeSize(r.sizeName()),
                limit(r.externalId(), 64, "Id категорії партнера"), limit(r.externalName(), 255, "Назва категорії партнера"),
                r.percentSupply(), r.percentSale())).toList();
        for (CategoryMapping m : mappings) {
            if (m.categoryId() <= 0) {
                throw new DomainException("Оберіть категорію в кожному рядку відповідності.");
            }
            checkPercent(m.percentSupply());
            checkPercent(m.percentSale());
        }
        checkUnique(mappings.stream().map(m -> new Key(m.categoryId(), m.sizeName())).toList(), "Категорія з таким розміром");

        merge(categoryMap, mappings, (row, m) -> row.getCategoryId() == m.categoryId() && Objects.equals(row.getSizeName(), m.sizeName()),
                m -> new PartnerCategoryMap(m.categoryId(), m.sizeName()), PartnerCategoryMap::apply);
    }

    /** Replaces the list of single products. As with categories, existing rows are updated in place. */
    public void setProductMap(List<ProductMapping> rows) {
        List<ProductMapping> mappings = rows.stream().map(r -> new ProductMapping(r.productId(), normalizeSize(r.sizeName()),
                limit(r.externalId(), 64, "Артикул партнера"), limit(r.externalName(), 255, "Назва в партнера"),
                r.priceSupply(), r.priceSale())).toList();
        for (ProductMapping m : mappings) {
            if (m.productId() <= 0) {
                throw new DomainException("Оберіть товар у кожному рядку.");
            }
            if ((m.priceSupply() != null && m.priceSupply().signum() <= 0) || (m.priceSale() != null && m.priceSale().signum() <= 0)) {
                throw new DomainException("Ціна для партнера має бути більшою за нуль.");
            }
        }
        checkUnique(mappings.stream().map(m -> new Key(m.productId(), m.sizeName())).toList(), "Товар з таким розміром");

        merge(productMap, mappings, (row, m) -> row.getProductId() == m.productId() && Objects.equals(row.getSizeName(), m.sizeName()),
                m -> new PartnerProductMap(m.productId(), m.sizeName()), PartnerProductMap::apply);
    }

    private record Key(int id, String size) {
    }

    private static <R, M> void merge(List<R> rows, List<M> mappings, BiPredicate<R, M> same, Function<M, R> create, BiConsumer<R, M> apply) {
        rows.removeIf(row -> mappings.stream().noneMatch(m -> same.test(row, m)));
        for (M mapping : mappings) {
            R row = rows.stream().filter(r -> same.test(r, mapping)).findFirst().orElse(null);
            if (row == null) {
                row = create.apply(mapping);
                rows.add(row);
            }
            apply.accept(row, mapping);
        }
    }

    private static void checkUnique(List<Key> keys, String what) {
        var seen = new HashSet<Key>();
        for (Key key : keys) {
            if (!seen.add(key)) {
                throw new DomainException(what + " (" + key.id() + (key.size() == null ? "" : ", " + key.size()) + ") вказано двічі.");
            }
        }
    }

    private static void checkPercent(BigDecimal percent) {
        if (percent != null && percent.compareTo(MINUS_HUNDRED) <= 0) {
            throw new DomainException("Відсоток не може зменшувати ціну до нуля чи нижче (має бути більшим за −100).");
        }
    }

    /** An empty size means "all sizes" and is stored as NULL, as on the old site. */
    private static String normalizeSize(String size) {
        return size == null || size.isBlank() ? null : size.strip();
    }

    private static String limit(String value, int maxLength, String field) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.length() > maxLength) {
            throw new DomainException(field + " — не довше " + maxLength + " символів.");
        }
        return trimmed;
    }

    public Integer getId() {
        return id;
    }

    /** A short Latin code; the feed address is made from it (/feeds/{code}.xml). */
    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    /** The feed address for the partner, for reference, as on the old site. NewErp itself serves the feed. */
    public String getFeedUrl() {
        return feedUrl;
    }

    public boolean isFeedEnabled() {
        return feedEnabled;
    }

    /** The brand (vendor) in the feed. */
    public String getVendorName() {
        return vendorName;
    }

    public String getNotes() {
        return notes;
    }

    /** The quantity the feed shows for each offer. */
    public int getDefaultQuantity() {
        return defaultQuantity;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }

    public LocalDateTime getDateModified() {
        return dateModified;
    }

    public List<PartnerCategoryMap> getCategoryMap() {
        return Collections.unmodifiableList(categoryMap);
    }

    public List<PartnerProductMap> getProductMap() {
        return Collections.unmodifiableList(productMap);
    }
}
