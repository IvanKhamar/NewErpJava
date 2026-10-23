package ua.newerp.domain.production;

import static ua.newerp.domain.common.Decimals.greater;
import static ua.newerp.domain.common.Decimals.isNegative;
import static ua.newerp.domain.common.Decimals.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.common.Text;

/**
 * A garment model (dd_stock_production): sizes with cut and sewn stock, links to shop products
 * and the sewing BOM. Cut components are separate objects ({@link ProductionComponent}).
 */
public class ProductionModel {

    /** Own sewing: cut → sew. */
    public static final String CLOTHING = "clothing";

    /** Ready-made goods from a supplier: only the "sewn" stock. */
    public static final String READY_MADE = "ready_made";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record SizeInput(String name, BigDecimal percent) {
    }

    public record CategoryLinkInput(int categoryId, String nameFilter, boolean matchExact) {
    }

    public record SizeOption(int sizeId, Integer optionValueId) {
    }

    public record SewnBomRow(Integer sizeId, Integer componentId, Integer itemId, BigDecimal qtyPerUnit) {
    }

    private Integer id;
    private String name = "";
    private String type = CLOTHING;
    private int minTotal;
    private int sortOrder;
    private boolean active = true;
    private List<ProductionSize> sizes = new ArrayList<>();
    private List<ProductionProductLink> productLinks = new ArrayList<>();
    private List<ProductionCategoryLink> categoryLinks = new ArrayList<>();
    private List<SewnBomLine> sewnBom = new ArrayList<>();

    protected ProductionModel() {
    }

    public ProductionModel(String name, String type, int minTotal, int sortOrder) {
        update(name, type, minTotal, sortOrder);
    }

    public void update(String name, String type, int minTotal, int sortOrder) {
        if (!CLOTHING.equals(type) && !READY_MADE.equals(type)) {
            throw new DomainException("Тип моделі має бути " + CLOTHING + " або " + READY_MADE + ".");
        }
        if (minTotal < 0) {
            throw new DomainException("Мінімальний запас не може бути від'ємним.");
        }
        this.name = Text.require(name, "Назва моделі", 150);
        this.type = type;
        this.minTotal = minTotal;
        this.sortOrder = sortOrder;
    }

    public void deactivate() {
        active = false;
    }

    /**
     * Sets the list of sizes. Sizes are matched by name: existing ones keep their id and stock,
     * new ones are added, and only a size without stock and without sewing BOM rows can be removed.
     * The old module recreated all sizes instead, so their ids changed.
     *
     * @return ids of removed sizes, so the use case can check nothing else refers to them
     */
    public List<Integer> setSizes(List<SizeInput> input) {
        List<String> names = input.stream().map(s -> Text.require(s.name(), "Назва розміру", 32)).toList();
        names.stream()
                .filter(n -> names.stream().filter(n::equalsIgnoreCase).count() > 1)
                .findFirst()
                .ifPresent(dup -> {
                    throw new DomainException("Розмір \"" + dup + "\" указано двічі.");
                });
        if (input.stream().anyMatch(s -> isNegative(s.percent()) || greater(s.percent(), HUNDRED))) {
            throw new DomainException("Частка розміру має бути від 0 до 100 %.");
        }

        List<ProductionSize> removed = sizes.stream()
                .filter(s -> names.stream().noneMatch(s.getName()::equalsIgnoreCase))
                .toList();
        for (ProductionSize size : removed) {
            if (size.getCutStock().signum() != 0 || size.getSewnStock().signum() != 0) {
                throw new DomainException("Розмір \"" + size.getName() + "\" не можна прибрати: на ньому є залишок.");
            }
            if (sewnBom.stream().anyMatch(l -> Objects.equals(l.getSizeId(), size.getId()))) {
                throw new DomainException("Розмір \"" + size.getName() + "\" використовується в специфікації пошиву.");
            }
            sizes.remove(size);
        }

        for (int i = 0; i < names.size(); i++) {
            String sizeName = names.get(i);
            ProductionSize existing = sizes.stream().filter(s -> s.getName().equalsIgnoreCase(sizeName)).findFirst().orElse(null);
            if (existing == null) {
                sizes.add(new ProductionSize(sizeName, input.get(i).percent(), i + 1));
            } else {
                existing.setPercent(input.get(i).percent());
                existing.setSortOrder(i + 1);
            }
        }

        return removed.stream().map(ProductionSize::getId).toList();
    }

    /** Replaces the links to OpenCart products: directly and through categories with a name filter. */
    public void setLinks(Collection<Integer> productIds, Collection<CategoryLinkInput> categories) {
        if (productIds.stream().anyMatch(id -> id <= 0) || categories.stream().anyMatch(c -> c.categoryId() <= 0)) {
            throw new DomainException("Id товарів і категорій мають бути додатними.");
        }
        List<Integer> categoryIds = categories.stream().map(CategoryLinkInput::categoryId).toList();
        categoryIds.stream()
                .filter(id -> Collections.frequency(categoryIds, id) > 1)
                .findFirst()
                .ifPresent(dup -> {
                    throw new DomainException("Категорію " + dup + " указано двічі.");
                });

        Set<Integer> wanted = new LinkedHashSet<>(productIds);
        productLinks.removeIf(l -> !wanted.contains(l.getProductId()));
        for (int productId : wanted) {
            if (productLinks.stream().noneMatch(l -> l.getProductId() == productId)) {
                productLinks.add(new ProductionProductLink(productId));
            }
        }

        categoryLinks.removeIf(l -> !categoryIds.contains(l.getCategoryId()));
        for (CategoryLinkInput c : categories) {
            String nameFilter = Text.orEmpty(c.nameFilter());
            categoryLinks.stream()
                    .filter(l -> l.getCategoryId() == c.categoryId())
                    .findFirst()
                    .ifPresentOrElse(
                            link -> link.setFilter(nameFilter, c.matchExact()),
                            () -> categoryLinks.add(new ProductionCategoryLink(c.categoryId(), nameFilter, c.matchExact())));
        }
    }

    /** Links the model's sizes to values of the "Size" option of OpenCart products. */
    public void setSizeOptions(Collection<SizeOption> mappings) {
        for (SizeOption mapping : mappings) {
            requireSize(mapping.sizeId()).setOptionValueId(mapping.optionValueId());
        }
    }

    /**
     * How many pieces to show on the storefront for each linked value of the "Size" option: the size's sewn stock,
     * rounded down to whole pieces (OpenCart quantities are whole). If two sizes are linked to the same value,
     * the first one wins, as in the old module.
     */
    public Map<Integer, Integer> storefrontQuantities() {
        var result = new LinkedHashMap<Integer, Integer>();
        sizes.stream()
                .filter(s -> s.getOptionValueId() != null)
                .sorted(Comparator.comparingInt(ProductionSize::getSortOrder).thenComparing(ProductionSize::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(s -> result.putIfAbsent(s.getOptionValueId(),
                        s.getSewnStock().max(BigDecimal.ZERO).setScale(0, RoundingMode.FLOOR).intValueExact()));
        return result;
    }

    /** Replaces the sewing BOM. Each row is either a cut component of this model or a consumable, not both. */
    public void setSewnBom(Collection<SewnBomRow> rows, Collection<Integer> componentIdsOfModel) {
        for (SewnBomRow row : rows) {
            if ((row.componentId() == null) == (row.itemId() == null)) {
                throw new DomainException("Рядок специфікації пошиву: вкажіть або деталь крою, або розхідник.");
            }
            if (row.qtyPerUnit().signum() <= 0) {
                throw new DomainException("Кількість на один виріб має бути більшою за нуль.");
            }
            if (row.sizeId() != null) {
                requireSize(row.sizeId());
            }
            if (row.componentId() != null && !componentIdsOfModel.contains(row.componentId())) {
                throw new DomainException("Деталь " + row.componentId() + " не належить цій моделі.");
            }
        }
        var keys = new HashSet<List<Integer>>();
        if (!rows.stream().allMatch(r -> keys.add(Arrays.asList(r.sizeId(), r.componentId(), r.itemId())))) {
            throw new DomainException("Специфікація пошиву містить повторювані рядки.");
        }

        sewnBom.clear();
        rows.forEach(r -> sewnBom.add(new SewnBomLine(r.sizeId(), r.componentId(), r.itemId(), r.qtyPerUnit())));
    }

    /** Removes a component from the sewing BOM when the component is deleted. */
    public void removeComponentFromSewnBom(int componentId) {
        sewnBom.removeIf(l -> Objects.equals(l.getComponentId(), componentId));
    }

    /** "+Cut": {@code quantity} sets of the size were cut. */
    public ProductionLogEntry recordCut(int sizeId, BigDecimal quantity, String comment, int userId, String username,
            LocalDateTime now) {
        return change(requireSize(sizeId), ProductionLogEntry.CUT, requirePositive(quantity), comment, userId, username, now);
    }

    /** "To sewing": cut sets were passed to the seamstresses. Not more than was cut. */
    public ProductionLogEntry sendToSewing(int sizeId, BigDecimal quantity, String comment, int userId, String username,
            LocalDateTime now) {
        ProductionSize size = requireSize(sizeId);
        requireAvailable(size, size.getCutStock(), quantity, "розкроєно");
        return change(size, ProductionLogEntry.CUT, quantity.negate(), comment, userId, username, now);
    }

    /** "+Sewn": finished pieces of the size arrived in stock (for ready_made: a receipt from the supplier). */
    public ProductionLogEntry recordSewn(int sizeId, BigDecimal quantity, String comment, int userId, String username,
            LocalDateTime now) {
        return change(requireSize(sizeId), ProductionLogEntry.SEWN, requirePositive(quantity), comment, userId, username, now);
    }

    /** "Shipped": sewn pieces left the stock not through an order. Not more than there is. */
    public ProductionLogEntry ship(int sizeId, BigDecimal quantity, String comment, int userId, String username,
            LocalDateTime now) {
        ProductionSize size = requireSize(sizeId);
        requireAvailable(size, size.getSewnStock(), quantity, "пошито");
        return change(size, ProductionLogEntry.SEWN, quantity.negate(), comment, userId, username, now);
    }

    /**
     * Stocktaking: the cut ({@link ProductionLogEntry#CUT}) or sewn ({@link ProductionLogEntry#SEWN}) stock of the size becomes
     * what was counted, and the difference goes into the log. Returns null when the count matches.
     */
    public ProductionLogEntry recount(int sizeId, String stage, BigDecimal actual, String comment, int userId, String username,
            LocalDateTime now) {
        if (!ProductionLogEntry.CUT.equals(stage) && !ProductionLogEntry.SEWN.equals(stage)) {
            throw new DomainException("Етап має бути " + ProductionLogEntry.CUT + " або " + ProductionLogEntry.SEWN + ".");
        }
        ProductionSize size = requireSize(sizeId);
        if (actual == null || actual.signum() < 0 || actual.stripTrailingZeros().scale() > 3) {
            throw new DomainException("Розмір " + size.getName() + ": кількість має бути від 0, не більше 3 знаків після коми.");
        }
        BigDecimal before = ProductionLogEntry.CUT.equals(stage) ? size.getCutStock() : size.getSewnStock();
        BigDecimal delta = actual.subtract(before);
        return delta.signum() == 0 ? null : change(size, stage, delta, comment, userId, username, now);
    }

    /** Log comment the old site uses for sewn pieces taken by an order. */
    public static String orderComment(int orderId) {
        return "Замовлення #" + orderId;
    }

    /** Log comment for pieces returned when an order leaves the write-off statuses. */
    public static String cancelComment(int orderId) {
        return "Скасування #" + orderId;
    }

    /**
     * An order takes sewn pieces of the size. Like consumables, it takes what there is:
     * an order must not be blocked by an empty stock. Returns null when there is nothing to take.
     */
    public ProductionLogEntry takeSewnForOrder(int sizeId, BigDecimal wanted, int orderId, int userId, String username,
            LocalDateTime now) {
        ProductionSize size = requireSize(sizeId);
        BigDecimal amount = requirePositive(wanted).min(size.getSewnStock().max(BigDecimal.ZERO));
        return amount.signum() > 0
                ? change(size, ProductionLogEntry.SEWN, amount.negate(), orderComment(orderId), userId, username, now)
                : null;
    }

    /** The order left the write-off statuses: pieces go back to stock. */
    public ProductionLogEntry returnSewnFromOrder(int sizeId, BigDecimal quantity, int orderId, int userId, String username,
            LocalDateTime now) {
        return change(requireSize(sizeId), ProductionLogEntry.SEWN, requirePositive(quantity), cancelComment(orderId),
                userId, username, now);
    }

    private ProductionLogEntry change(ProductionSize size, String stage, BigDecimal delta, String comment, int userId,
            String username, LocalDateTime now) {
        String text = Text.orEmpty(comment);
        if (text.length() > 255) {
            throw new DomainException("Коментар: не довше 255 символів.");
        }
        boolean cut = ProductionLogEntry.CUT.equals(stage);
        BigDecimal before = cut ? size.getCutStock() : size.getSewnStock();
        BigDecimal after = before.add(delta);
        if (cut) {
            size.setCutStock(after);
        } else {
            size.setSewnStock(after);
        }
        return new ProductionLogEntry(this, size, stage, delta, before, after, text, userId, username, now);
    }

    private static BigDecimal requirePositive(BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw new DomainException("Кількість має бути більшою за нуль.");
        }
        return quantity;
    }

    private static void requireAvailable(ProductionSize size, BigDecimal available, BigDecimal quantity, String what) {
        requirePositive(quantity);
        if (greater(quantity, available)) {
            throw new DomainException("Розмір " + size.getName() + ": " + what + " лише " + text(available)
                    + ", а вказано " + text(quantity) + ".");
        }
    }

    public ProductionSize requireSize(int sizeId) {
        return sizes.stream()
                .filter(s -> Objects.equals(s.getId(), sizeId))
                .findFirst()
                .orElseThrow(() -> new DomainException("Розмір " + sizeId + " не належить моделі \"" + name + "\"."));
    }

    public BigDecimal getTotalSewn() {
        return sizes.stream().map(ProductionSize::getSewnStock).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    /** If fewer pieces are sewn in total, the model goes to the "needs sewing" list. */
    public int getMinTotal() {
        return minTotal;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /** false means "deleted": the old module sets status = 0 instead of deleting the row. */
    public boolean isActive() {
        return active;
    }

    public List<ProductionSize> getSizes() {
        return Collections.unmodifiableList(sizes);
    }

    public List<ProductionProductLink> getProductLinks() {
        return Collections.unmodifiableList(productLinks);
    }

    public List<ProductionCategoryLink> getCategoryLinks() {
        return Collections.unmodifiableList(categoryLinks);
    }

    public List<SewnBomLine> getSewnBom() {
        return Collections.unmodifiableList(sewnBom);
    }
}
