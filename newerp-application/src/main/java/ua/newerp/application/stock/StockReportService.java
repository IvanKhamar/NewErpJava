package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.domain.common.DomainException;

/**
 * Stock reports of the old CRM: what the stock is worth, how fast each consumable goes (and so what to order for the next
 * weeks, not just up to the minimum), and what production put out. Stocktaking corrections are shown apart, so they do not
 * look like use.
 */
@Service
@Transactional(readOnly = true)
public class StockReportService {

    public static final String NO_CATEGORY_NAME = "Без категорії";

    private static final Collator NAMES = Collator.getInstance(Locale.forLanguageTag("uk-UA"));

    private final StockRepository stock;
    private final PurchaseOrderRepository purchaseOrders;
    private final StockReportSource source;
    private final Clock clock;

    public StockReportService(StockRepository stock, PurchaseOrderRepository purchaseOrders, StockReportSource source, Clock clock) {
        this.stock = stock;
        this.purchaseOrders = purchaseOrders;
        this.source = source;
        this.clock = clock;
    }

    /** days: the period back from today (today included); coverDays: how many days the suggested order should last. */
    public StockReportDto get(int days, int coverDays) {
        if (days < 1 || days > 365 || coverDays < 1 || coverDays > 365) {
            throw new DomainException("Період і запас на скільки днів: від 1 до 365.");
        }
        LocalDate from = LocalDate.now(clock).minusDays(days - 1L);
        List<StockItemDto> items = stock.listItems(new StockItemFilter(null, false));

        List<CategoryValueDto> categories = values(items);
        Map<Integer, StockItemDto> byId = items.stream().collect(Collectors.toMap(StockItemDto::id, Function.identity()));
        Map<Integer, BigDecimal> onOrder = purchaseOrders.onOrder();
        List<ItemConsumptionDto> consumption = source.itemFlows(from.atStartOfDay()).stream()
                .filter(f -> byId.containsKey(f.itemId())) // archived items are left out
                .map(f -> consumption(byId.get(f.itemId()), f, onOrder.getOrDefault(f.itemId(), BigDecimal.ZERO), days, coverDays))
                .sorted(Comparator.comparing(ItemConsumptionDto::daysLeft, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ItemConsumptionDto::name, NAMES))
                .toList();

        return new StockReportDto(days, from, coverDays, categories, sum(categories, CategoryValueDto::value), consumption,
                sum(consumption, ItemConsumptionDto::sum), output(source.sizeFlows(from.atStartOfDay())));
    }

    /** Most valuable categories first, items without a category last. */
    static List<CategoryValueDto> values(List<StockItemDto> items) {
        return items.stream()
                // groupingBy needs a non-null key, so "no category" becomes Optional.empty()
                .collect(Collectors.groupingBy(i -> Optional.ofNullable(i.categoryId()), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(g -> new CategoryValueDto(g.getKey().orElse(null),
                        g.getKey().isPresent() ? g.getValue().getFirst().categoryName() : NO_CATEGORY_NAME, g.getValue().size(),
                        money(sum(g.getValue(), i -> i.quantity().max(BigDecimal.ZERO).multiply(i.price())))))
                .sorted(Comparator.comparing((CategoryValueDto c) -> c.categoryId() == null)
                        .thenComparing(CategoryValueDto::value, Comparator.reverseOrder())
                        .thenComparing(CategoryValueDto::name, NAMES))
                .toList();
    }

    /**
     * The pace is the average use of the period. suggested = use over coverDays − stock − already ordered, rounded up to whole
     * units like the minimum-based reorder.
     */
    static ItemConsumptionDto consumption(StockItemDto item, StockReportSource.ItemFlow flow, BigDecimal onOrder, int days,
            int coverDays) {
        BigDecimal onStock = item.quantity().max(BigDecimal.ZERO);
        BigDecimal used = flow.used();
        Integer daysLeft = used.signum() > 0
                ? onStock.multiply(BigDecimal.valueOf(days)).divide(used, 0, RoundingMode.FLOOR).intValueExact()
                : null;
        BigDecimal suggested = used.multiply(BigDecimal.valueOf(coverDays)).divide(BigDecimal.valueOf(days), 6, RoundingMode.HALF_UP)
                .subtract(onStock).subtract(onOrder).max(BigDecimal.ZERO).setScale(0, RoundingMode.CEILING);
        return new ItemConsumptionDto(item.id(), item.name(), item.unit(), used, flow.received(), flow.corrected(),
                used.divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP), item.quantity(), onOrder, daysLeft, suggested,
                money(suggested.multiply(item.price())));
    }

    /** Sizes come in model order; a model's totals are the sums of its sizes. */
    static List<ModelOutputDto> output(List<StockReportSource.SizeFlow> flows) {
        Map<Integer, List<StockReportSource.SizeFlow>> byModel = flows.stream()
                .collect(Collectors.groupingBy(StockReportSource.SizeFlow::productionId, LinkedHashMap::new, Collectors.toList()));
        var result = new ArrayList<ModelOutputDto>(byModel.size());
        byModel.forEach((id, sizes) -> result.add(new ModelOutputDto(id, sizes.getFirst().modelName(),
                sum(sizes, StockReportSource.SizeFlow::cut), sum(sizes, StockReportSource.SizeFlow::sewn),
                sum(sizes, StockReportSource.SizeFlow::sold), sum(sizes, StockReportSource.SizeFlow::shipped),
                sum(sizes, StockReportSource.SizeFlow::corrected),
                sizes.stream().map(s -> new SizeOutputDto(s.sizeId(), s.sizeName(), s.cut(), s.sewn(), s.sold(), s.shipped(),
                        s.corrected())).toList())));
        return result;
    }

    /** Kopecks: quantity × price has up to 7 decimals. */
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
