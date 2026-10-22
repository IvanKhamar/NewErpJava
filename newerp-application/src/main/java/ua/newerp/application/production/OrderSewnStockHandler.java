package ua.newerp.application.production;

import static ua.newerp.domain.common.Decimals.less;
import static ua.newerp.domain.common.Decimals.text;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.newerp.application.orders.OrderStatusHandler;
import ua.newerp.domain.orders.OrderStatusChange;
import ua.newerp.domain.production.ProductionLogEntry;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.domain.production.ProductionSize;

/**
 * Sewn pieces and orders (based on the old site's StockTrack event). When an order enters a status from
 * production.order-subtract-status-ids, pieces of the right size are written off; when it leaves, they come back.
 * The size comes from the product option in the order that is linked to a size of the model.
 */
@Component
public class OrderSewnStockHandler implements OrderStatusHandler {

    /** As on the old site: automatic log changes are signed "system", and user_id is whoever changed the status. */
    public static final String SYSTEM_USERNAME = "system";

    private record ModelSize(ProductionModel model, ProductionSize size) {
    }

    private final ProductionRepository repository;
    private final ProductionProperties properties;
    private final Clock clock;

    public OrderSewnStockHandler(ProductionRepository repository, ProductionProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<String> onStatusChanging(OrderStatusChange change) {
        List<Integer> statuses = properties.orderSubtractStatusIds();
        boolean wasIn = statuses.contains(change.oldStatusId());
        boolean isIn = statuses.contains(change.newStatusId());
        if (wasIn == isIn) {
            return List.of();
        }

        List<OrderSizeLine> lines = repository.getOrderSizeLines(change.orderId()).stream()
                .filter(l -> l.productionId() != null)
                .toList();
        if (lines.isEmpty()) {
            return List.of();
        }

        Map<Integer, ProductionModel> models = repository.findModels(lines.stream().map(OrderSizeLine::productionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ProductionModel::getId, Function.identity()));
        var warnings = new ArrayList<String>();
        // ModelSize is a record, so it works as a two-part key (like a tuple key in C#). Model and size compare
        // by reference here, which is right: each comes once from the loaded models.
        var bySize = new LinkedHashMap<ModelSize, BigDecimal>();

        for (OrderSizeLine line : lines) {
            ProductionModel model = models.get(line.productionId());
            if (model == null) {
                continue;
            }
            ProductionSize size = model.getSizes().stream()
                    .filter(s -> s.getOptionValueId() != null && line.optionValueIds().contains(s.getOptionValueId()))
                    .findFirst()
                    .orElse(null);
            if (size == null) {
                warnings.add("«" + line.productName() + "»: розмір із замовлення не зв'язаний з розмірами моделі «"
                        + model.getName() + "», склад не змінено.");
                continue;
            }
            bySize.merge(new ModelSize(model, size), BigDecimal.valueOf(line.quantity()), BigDecimal::add);
        }

        // How much the order already holds by the log. Thanks to this a repeated call (a failure after the write-off but
        // before the status was saved, or a write-off the old site already did) does not write off or return twice.
        Map<Integer, BigDecimal> held = repository.getSewnHeldByOrder(change.orderId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (isIn) {
            take(change, bySize, held, warnings, now);
        } else {
            giveBack(change, bySize, held, now);
        }
        return warnings;
    }

    /** Writes off what the order does not hold yet. If fewer pieces are sewn, takes what there is and warns. */
    private void take(OrderStatusChange change, Map<ModelSize, BigDecimal> bySize, Map<Integer, BigDecimal> held,
            List<String> warnings, LocalDateTime now) {
        bySize.forEach((key, quantity) -> {
            BigDecimal missing = quantity.subtract(held.getOrDefault(key.size().getId(), BigDecimal.ZERO));
            if (missing.signum() <= 0) {
                return;
            }
            ProductionLogEntry entry = key.model().takeSewnForOrder(key.size().getId(), missing, change.orderId(),
                    change.userId(), SYSTEM_USERNAME, now);
            if (entry != null) {
                repository.add(entry);
            }
            BigDecimal taken = entry == null ? BigDecimal.ZERO : entry.getQuantity();
            if (less(taken, missing)) {
                warnings.add("Пошитих «" + key.model().getName() + "» розміру " + key.size().getName() + " бракує "
                        + text(missing.subtract(taken)) + " шт.: списано " + text(taken) + " з " + text(missing) + ".");
            }
        });
    }

    /**
     * Returns not more than the order really holds by the log. The old module returned the full quantity even
     * when pieces were short at write-off, and so "created" pieces that never existed.
     */
    private void giveBack(OrderStatusChange change, Map<ModelSize, BigDecimal> bySize, Map<Integer, BigDecimal> held,
            LocalDateTime now) {
        bySize.forEach((key, quantity) -> {
            BigDecimal toReturn = quantity.min(held.getOrDefault(key.size().getId(), BigDecimal.ZERO));
            if (toReturn.signum() > 0) {
                repository.add(key.model().returnSewnFromOrder(key.size().getId(), toReturn, change.orderId(), change.userId(),
                        SYSTEM_USERNAME, now));
            }
        });
    }
}
