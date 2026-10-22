package ua.newerp.application.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "What to reorder" report: items below minimum, grouped by supplier. */
@Service
@Transactional(readOnly = true)
public class ReorderService {

    public static final String NO_SUPPLIER_NAME = "Без постачальника";

    private static final Collator UKRAINIAN = Collator.getInstance(Locale.of("uk", "UA"));

    private final StockRepository repository;
    private final PurchaseOrderRepository purchaseOrders;
    private final BigDecimal targetFactor;

    /**
     * @param targetFactor up to how many minimums to reorder: 2 means "bring the quantity up to 2 × minimum",
     *                     so the item does not drop below the minimum right after a receipt.
     *                     {@code @Value} reads one setting from application.yml.
     */
    public ReorderService(StockRepository repository, PurchaseOrderRepository purchaseOrders,
            @Value("${stock.reorder-target-factor:2}") BigDecimal targetFactor) {
        this.repository = repository;
        this.purchaseOrders = purchaseOrders;
        this.targetFactor = targetFactor;
    }

    /** Already ordered and not received is subtracted, so a repeated report does not suggest the same twice. */
    public ReorderReportDto getReport() {
        List<StockItemDto> items = repository.listItems(StockItemFilter.BELOW_MINIMUM);
        Map<Integer, StockSupplierDto> suppliers = repository.listSuppliers().stream()
                .collect(Collectors.toMap(StockSupplierDto::id, Function.identity()));
        return build(items, suppliers, purchaseOrders.onOrder(), targetFactor);
    }

    /**
     * The calculation without a database: suggested = factor × minimum − quantity − already ordered, rounded up.
     * Items that need nothing (all already ordered) are left out.
     */
    public static ReorderReportDto build(List<StockItemDto> items, Map<Integer, StockSupplierDto> suppliers,
            Map<Integer, BigDecimal> onOrder, BigDecimal targetFactor) {
        record Entry(StockItemDto item, ReorderLineDto line) {
        }

        // groupingBy needs a non-null key, so "no supplier" becomes Optional.empty() for the grouping
        Map<Optional<Integer>, List<Entry>> groups = items.stream()
                .filter(StockItemDto::isBelowMinimum)
                .map(i -> {
                    BigDecimal ordered = onOrder.getOrDefault(i.id(), BigDecimal.ZERO);
                    BigDecimal suggested = targetFactor.multiply(i.minQuantity()).subtract(i.quantity()).subtract(ordered)
                            .max(BigDecimal.ZERO).setScale(0, RoundingMode.CEILING);
                    return new Entry(i, new ReorderLineDto(i.id(), i.name(), i.unit(), i.quantity(), i.minQuantity(),
                            ordered, suggested, i.price(), suggested.multiply(i.price())));
                })
                .filter(e -> e.line().suggested().signum() > 0)
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.item().supplierId()),
                        LinkedHashMap::new, Collectors.toList()));

        List<SupplierReorderDto> result = groups.entrySet().stream()
                .map(g -> {
                    Integer supplierId = g.getKey().orElse(null);
                    StockSupplierDto supplier = supplierId == null ? null : suppliers.get(supplierId);
                    List<ReorderLineDto> lines = g.getValue().stream()
                            .map(Entry::line)
                            .sorted(Comparator.comparing(ReorderLineDto::name, UKRAINIAN))
                            .toList();
                    return new SupplierReorderDto(supplierId,
                            supplier == null ? NO_SUPPLIER_NAME : supplier.name(),
                            supplier == null ? "" : supplier.phone(),
                            supplier == null ? "" : supplier.email(),
                            lines, sum(lines), messageText(lines));
                })
                // Suppliers alphabetically, items without a supplier last.
                .sorted(Comparator.comparing((SupplierReorderDto s) -> s.supplierId() == null)
                        .thenComparing(SupplierReorderDto::supplierName, UKRAINIAN))
                .toList();

        return new ReorderReportDto(result, result.stream().map(SupplierReorderDto::total).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Text that can be copied to the supplier right away. */
    public static String messageText(List<ReorderLineDto> lines) {
        StringBuilder text = new StringBuilder("Добрий день! Хочемо замовити:");
        for (ReorderLineDto line : lines) {
            // like {x:0.###}: up to three decimals, no trailing zeros
            String quantity = line.suggested().setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            text.append("\n— ").append(line.name()).append(": ").append(quantity).append(' ').append(line.unit());
        }
        return text.append("\nДякуємо!").toString();
    }

    private static BigDecimal sum(List<ReorderLineDto> lines) {
        return lines.stream().map(ReorderLineDto::sum).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
