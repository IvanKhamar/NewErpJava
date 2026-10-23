package ua.newerp.application.catalog;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.catalog.ProductSorter;
import ua.newerp.domain.catalog.SortGroup;
import ua.newerp.domain.catalog.SortGroupType;
import ua.newerp.domain.catalog.SortPin;
import ua.newerp.domain.common.DomainException;

/**
 * The order of products on the storefront by sales, as the old site's "Порядок товарів" module: in each group (category, color,
 * theme, label, cross-sell panel) products go by sales in a period descending, the ones pinned by hand stand on their positions.
 * Before each change of sort_order a snapshot is saved, so the previous order can be restored.
 */
@Service
@Transactional
public class ProductSortService {

    private static final int SEARCH_LIMIT = 15;
    private static final int SNAPSHOT_LIMIT = 50;

    private final ProductSortStore store;
    private final ProductSortProperties options;
    private final Clock clock;

    public ProductSortService(ProductSortStore store, ProductSortProperties options, Clock clock) {
        this.store = store;
        this.options = options;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SortGroupDto> listGroups() {
        return store.listGroups().stream().map(g -> new SortGroupDto(g.type().ordinal(), g.key(), g.name())).toList();
    }

    /** The group's new order without writing it: it can be looked at, changed by hand and written with apply. */
    @Transactional(readOnly = true)
    public List<SortPreviewRow> preview(SortGroupType type, String key, SalesPeriod period) {
        SortGroup group = findGroup(type, key);
        Calculation calculation = calculate(group, toRange(period));
        List<Integer> order = calculation.order();
        if (order.isEmpty()) {
            return List.of();
        }

        Map<Integer, SortProductDto> products = store.products(order, group.type() == SortGroupType.CROSS_SELL).stream()
                .collect(Collectors.toMap(SortProductDto::productId, Function.identity(), (a, b) -> a));
        // The current position is the product's place among the same group's products by their present sort_order, not sort_order itself.
        List<Integer> byCurrent = order.stream()
                .sorted(Comparator.comparingInt((Integer id) -> products.containsKey(id) ? products.get(id).sortOrder() : 0)
                        .thenComparingInt(id -> id))
                .toList();
        Map<Integer, Integer> current = new HashMap<>();
        for (int i = 0; i < byCurrent.size(); i++) {
            current.put(byCurrent.get(i), i + 1);
        }

        var rows = new ArrayList<SortPreviewRow>();
        for (int i = 0; i < order.size(); i++) {
            int id = order.get(i);
            SortProductDto product = products.get(id);
            rows.add(new SortPreviewRow(id, product == null ? "(товар видалено)" : product.name(), product == null ? "" : product.image(),
                    calculation.sales().getOrDefault(id, 0), calculation.pinned().contains(id), current.get(id), i + 1));
        }
        return rows;
    }

    /** Writes a ready order of a group (for example, a preview changed by hand). */
    public SortResult apply(ApplySortRequest request) {
        SortGroup group = findGroup(SortGroupType.fromOrdinal(request.type()), request.key());
        List<Integer> ids = request.productIds() == null ? List.of() : request.productIds();
        if (ids.isEmpty()) {
            throw new DomainException("Немає товарів для запису — спершу переглянь новий порядок.");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new DomainException("Товар у порядку повторюється.");
        }

        Integer snapshot = snapshotUnlessCrossSell(group, "Порядок групи: " + group.name());
        return new SortResult(snapshot, store.apply(group.type() == SortGroupType.CROSS_SELL, ids));
    }

    public SortResult recalculateGroup(RecalculateGroupRequest request) {
        SortGroup group = findGroup(SortGroupType.fromOrdinal(request.type()), request.key());
        Range range = toRange(request.period());
        Integer snapshot = snapshotUnlessCrossSell(group, "Перерахунок групи: " + group.name());
        List<Integer> order = calculate(group, range).order();
        return new SortResult(snapshot, order.isEmpty() ? 0 : store.apply(group.type() == SortGroupType.CROSS_SELL, order));
    }

    /**
     * Recalculates all groups in turn (themes, categories, labels, the cross-sell panel), one snapshot before the start.
     * A product has one sort_order, so a product of several groups gets its position from the last of them, as on the old site.
     */
    public SortResult recalculateAll(SalesPeriod period) {
        Range range = toRange(period);
        int snapshot = store.createSnapshot("Перед перерахунком усіх груп", now());
        int updated = 0;
        for (SortGroup group : store.listGroups()) {
            List<Integer> order = calculate(group, range).order();
            if (!order.isEmpty()) {
                updated += store.apply(group.type() == SortGroupType.CROSS_SELL, order);
            }
        }
        return new SortResult(snapshot, updated);
    }

    @Transactional(readOnly = true)
    public List<SortSnapshotDto> listSnapshots() {
        return store.listSnapshots(SNAPSHOT_LIMIT);
    }

    public void restoreSnapshot(int id) {
        if (!store.restoreSnapshot(id)) {
            throw new NotFoundException("Знімка " + id + " не знайдено.");
        }
    }

    @Transactional(readOnly = true)
    public List<SortPinDto> listPins() {
        return store.listPins();
    }

    public void setPin(SetPinRequest request) {
        if (request.position() < 1) {
            throw new DomainException("Позиція рахується з 1.");
        }
        SortGroup group = findGroup(SortGroupType.fromOrdinal(request.type()), request.key());
        if (store.products(List.of(request.productId()), false).isEmpty()) {
            throw new DomainException("Товару " + request.productId() + " не знайдено.");
        }
        store.setPin(group, request.position(), request.productId(), now());
    }

    public void removePin(int id) {
        if (!store.removePin(id)) {
            throw new NotFoundException("Закріплення " + id + " не знайдено.");
        }
    }

    @Transactional(readOnly = true)
    public CrossSellMetricDto getCrossSellMetric() {
        return new CrossSellMetricDto(store.crossSellPanelOnly());
    }

    public CrossSellMetricDto setCrossSellMetric(CrossSellMetricDto metric) {
        store.setCrossSellPanelOnly(metric.panelOnly());
        return metric;
    }

    @Transactional(readOnly = true)
    public List<SortProductDto> searchProducts(String term) {
        return term == null || term.strip().length() < 2 ? List.of() : store.searchProducts(term.strip(), SEARCH_LIMIT);
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }

    private record Calculation(List<Integer> order, Map<Integer, Integer> sales, Set<Integer> pinned) {
    }

    private Calculation calculate(SortGroup group, Range range) {
        List<Integer> productIds = store.groupProductIds(group);
        if (productIds.isEmpty()) {
            return new Calculation(List.of(), Map.of(), Set.of());
        }

        boolean crossSellOnly = group.type() == SortGroupType.CROSS_SELL && store.crossSellPanelOnly();
        Map<Integer, Integer> sales = store.sales(productIds, range.from(), range.to(), crossSellOnly);
        // The "Новинка" label stays from the newest product, without sales.
        boolean isNew = group.type() == SortGroupType.LABEL
                && group.key().toLowerCase(Locale.ROOT).equals(options.newLabelName().toLowerCase(Locale.ROOT));
        List<Integer> queue = isNew ? productIds : ProductSorter.bySales(productIds, sales);

        List<SortPin> pins = store.pins(group);
        return new Calculation(ProductSorter.merge(queue, pins), sales,
                pins.stream().map(SortPin::productId).collect(Collectors.toSet()));
    }

    private SortGroup findGroup(SortGroupType type, String key) {
        return store.listGroups().stream()
                .filter(g -> g.type() == type && g.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Групи «" + key + "» немає (у ній мають бути увімкнені товари)."));
    }

    private Integer snapshotUnlessCrossSell(SortGroup group, String note) {
        return group.type() == SortGroupType.CROSS_SELL ? null : store.createSnapshot(note, now());
    }

    private Range toRange(SalesPeriod period) {
        LocalDateTime now = now();
        if (period != null && period.from() != null && period.to() != null) {
            if (period.from().isAfter(period.to())) {
                throw new DomainException("Початок періоду пізніший за кінець.");
            }
            return new Range(period.from().atStartOfDay(), period.to().atTime(LocalTime.MAX));
        }
        if (period != null && (period.from() != null || period.to() != null)) {
            throw new DomainException("Вкажи обидві дати періоду.");
        }
        int days = period != null && period.days() != null && period.days() > 0 ? period.days() : options.defaultDays();
        return new Range(now.minusDays(days), now);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
